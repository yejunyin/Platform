package com.enterprise.brain.task.wms.picktask.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.enterprise.brain.task.wms.dto.WmsPickingSendResponse;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTask;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskEntry;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskLog;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskEntryMapper;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskLogMapper;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskMapper;
import com.enterprise.brain.task.service.WmsService;
import com.enterprise.brain.task.wms.service.WmsPickingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.brain.task.wms.service.WmsShelfInventoryCache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时领料任务调度器（料架驱动模式）
 *
 * 与 OneClickPickScheduler 使用相同的料架驱动策略：
 *  1. 每轮先查 WMS 料架库存 → Map<productionNo, Map<materialCode, onShelfQty>>
 *  2. 过滤本轮可推物料：sentQty < totalQty 且料架上该物料数量 == 0
 *  3. 组装 WMS ProductionPicking JSON 并推送
 *  4. 剩余 ≤8 种物料时放宽料架限制
 *  5. 单次最多 48 种物料（WMS 6 货架 × 48 箱 限制）
 */
@Slf4j
@Component
public class PickTaskScheduler {

    @Resource
    private WmsPickTaskMapper taskMapper;

    @Resource
    private WmsPickTaskEntryMapper entryMapper;

    @Resource
    private WmsPickTaskLogMapper logMapper;

    @Resource
    private WmsService wmsService;

    @Resource
    private WmsPickingService pickingService;

    @Resource
    private WmsShelfInventoryCache shelfInventoryCache;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Value("${pick-task.enabled:false}")
    private boolean enabled;

    @Value("${pick-task.max-round-materials:48}")
    private int maxRoundMaterials;

    @Value("${pick-task.relax-threshold:8}")
    private int relaxThreshold;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ScheduledThreadPoolExecutor scheduler;
    private final ConcurrentMap<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.warn("[定时领料] pick-task.enabled=false，调度器已禁用");
            return;
        }
        scheduler = new ScheduledThreadPoolExecutor(4, r -> {
            Thread t = new Thread(r, "PickTaskScheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        restoreRunningTasks();
    }

    private void restoreRunningTasks() {
        List<WmsPickTask> running = taskMapper.selectList(
                new QueryWrapper<WmsPickTask>().eq("status", "running"));
        log.info("[定时领料] 启动恢复 running 任务数: {}", running.size());
        for (WmsPickTask task : running) {
            scheduleNext(task, task.getNextFireTime());
        }
    }

    public void scheduleNext(WmsPickTask task, long delaySec) {
        if (!enabled) {
            log.warn("[定时领料] enabled=false，忽略 schedule taskId={}", task.getId());
            return;
        }
        if (scheduler == null) {
            log.warn("[定时领料] scheduler 未初始化，忽略 schedule taskId={}", task.getId());
            return;
        }
        long taskId = task.getId();
        AtomicBoolean running = runningFlags.computeIfAbsent(taskId, k -> new AtomicBoolean(true));
        if (!running.get()) {
            log.info("[定时领料] taskId={} 已暂停/取消，跳过 schedule", taskId);
            return;
        }

        ScheduledFuture<?> oldFuture = futures.get(taskId);
        if (oldFuture != null && !oldFuture.isDone()) oldFuture.cancel(false);

        ScheduledFuture<?> future = scheduler.schedule(() -> executeOneRound(taskId),
                delaySec, TimeUnit.SECONDS);
        futures.put(taskId, future);
        log.info("[定时领料] taskId={} 已 schedule，delay={}s", taskId, delaySec);
    }

    public void scheduleNext(WmsPickTask task, LocalDateTime nextFireTime) {
        long delaySec = nextFireTime == null ? 0
                : Math.max(0, Duration.between(LocalDateTime.now(), nextFireTime).getSeconds());
        scheduleNext(task, delaySec);
    }

    /**
     * 执行一轮（料架驱动）
     */
    private void executeOneRound(long taskId) {
        log.info("[定时领料] ===== 开始执行 taskId={} 一轮（料架驱动） =====", taskId);

        WmsPickTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[定时领料] taskId={} 不存在", taskId);
            runningFlags.remove(taskId); futures.remove(taskId);
            return;
        }
        AtomicBoolean running = runningFlags.get(taskId);
        if (running == null || !running.get()) {
            log.info("[定时领料] taskId={} 已暂停/取消，本轮跳过", taskId);
            return;
        }
        if (!"running".equals(task.getStatus())) {
            log.info("[定时领料] taskId={} status={}，不执行", taskId, task.getStatus());
            runningFlags.remove(taskId); futures.remove(taskId);
            return;
        }

        List<WmsPickTaskEntry> entries = entryMapper.selectList(
                new QueryWrapper<WmsPickTaskEntry>().eq("task_id", taskId));

        // 检查全部发满
        boolean allDone = true;
        int remainingCount = 0;
        for (WmsPickTaskEntry e : entries) {
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) > 0) { allDone = false; remainingCount++; }
        }
        if (allDone) { completeTask(task, "所有明细发料已满"); return; }

        // 查料架缓存
        Map<String, Map<String, BigDecimal>> shelfMap = shelfInventoryCache.getShelfInventory();
        Map<String, BigDecimal> shelfForTask = shelfMap.getOrDefault(
                task.getProductionNo() != null ? task.getProductionNo().toUpperCase() : "",
                Collections.emptyMap());
        boolean shelfAvailable = shelfInventoryCache.isAvailable();

        boolean relax = remainingCount <= relaxThreshold;
        log.info("[定时领料] taskId={} remainingCount={} relax={} shelfAvailable={}",
                taskId, remainingCount, relax, shelfAvailable);

        // 筛选本轮可推（先走正常模式：料架有就不推；如果全被料架阻塞，再强制松绑推）
        BigDecimal[] roundQtyArr = new BigDecimal[entries.size()];
        Arrays.fill(roundQtyArr, BigDecimal.ZERO);

        int pickedMaterials = 0;
        for (int i = 0; i < entries.size(); i++) {
            WmsPickTaskEntry e = entries.get(i);
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) <= 0) continue;

            boolean blockByShelf = false;
            if (shelfAvailable && !relax) {
                BigDecimal onShelf = shelfForTask.getOrDefault(
                        e.getMaterialCode() != null ? e.getMaterialCode().toUpperCase() : "", BigDecimal.ZERO);
                if (onShelf.compareTo(BigDecimal.ZERO) > 0) {
                    blockByShelf = true;
                    log.info("[定时领料] taskId={} 物料 {} 料架上有 {}，暂不推送",
                            taskId, e.getMaterialCode(), onShelf);
                }
            }
            if (blockByShelf) continue;

            BigDecimal roundQty = left.min(e.getPerBoxQty());
            if (roundQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            roundQtyArr[i] = roundQty;
            pickedMaterials++;
            if (pickedMaterials >= maxRoundMaterials) break;
        }

        // 如果全被料架阻塞 + 还有剩余未发满 → 强制跳过料架约束再推一次（防卡死）
        if (pickedMaterials == 0 && remainingCount > 0) {
            log.info("[定时领料] taskId={} 全部 {} 种物料被料架阻塞，强制松绑重试", taskId, remainingCount);
            Arrays.fill(roundQtyArr, BigDecimal.ZERO);
            pickedMaterials = 0;
            for (int i = 0; i < entries.size(); i++) {
                if (pickedMaterials >= maxRoundMaterials) break;
                WmsPickTaskEntry e = entries.get(i);
                BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
                if (left.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal roundQty = left.min(e.getPerBoxQty());
                if (roundQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                roundQtyArr[i] = roundQty;
                pickedMaterials++;
            }
        }

        if (pickedMaterials == 0) {
            log.info("[定时领料] taskId={} 本轮无可推物料（全部发满），安排下一次轮询", taskId);
            scheduleNext(task, task.getIntervalSec() == null ? 60 : task.getIntervalSec() * 60);
            return;
        }

        // 组装 WMS JSON
        String billNo = pickingService.generateNextBillNo(task.getProductionNo());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("BillCode", billNo);
        data.put("ProdctionNo", task.getProductionNo());
        data.put("ProductCode", task.getProductCode());
        data.put("ProductName", task.getProductName());
        data.put("OrderTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "+08:00");
        data.put("OrganizationCode", task.getOrgCode());
        data.put("DepartmentCode", task.getDeptCode());
        data.put("OrderType", 1);
        data.put("DocumentType", "ProductionPicking");
        data.put("CustomerCode", null);
        data.put("CostCenterCode", task.getOrgCode());
        data.put("LineCode", task.getLineCode());

        List<Map<String, Object>> wmsEntries = new ArrayList<>();
        int itemNo = 1;
        for (int i = 0; i < entries.size(); i++) {
            BigDecimal qty = roundQtyArr[i];
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            WmsPickTaskEntry e = entries.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ItemNo", itemNo++);
            m.put("ItemId", toIntOrZero(e.getItemId()));
            m.put("MaterialCode", e.getMaterialCode());
            m.put("BatchCode", null);
            m.put("Quantity", qty);
            m.put("UnitCode", e.getUnitCode() != null ? e.getUnitCode() : "");
            m.put("PlanCode", e.getPlanCode() != null ? e.getPlanCode() : "");
            m.put("ProductionStationCode", e.getStationCode() != null ? e.getStationCode() : "3SC01");
            m.put("OutWarehouseCode", e.getWarehouseCode() != null ? e.getWarehouseCode() : "2010");
            wmsEntries.add(m);
        }
        data.put("Entries", wmsEntries);

        log.info("[定时领料] taskId={} round={} billNo={} 本轮物料={} 种 剩余总数={} 种",
                taskId, task.getCurrentRound() + 1, billNo, pickedMaterials, remainingCount);
        try {
            log.info("[定时领料] ====== 推送 WMS JSON ======\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (Exception ignored) {}

        // 推 WMS
        String logStatus, logMessage;
        String wmsTaskNo = null, requestId = null;
        boolean wmsSuccess = false;
        try {
            WmsPickingSendResponse resp = wmsService.sendProductionPicking(data, task.getLineCode());
            logMessage = resp.getMessage();
            if (resp.getData() != null) {
                wmsTaskNo = resp.getData().getWmsTaskNo();
                requestId = resp.getData().getRequestId();
            }
            if (Boolean.TRUE.equals(resp.getSuccess())) {
                logStatus = "sent"; wmsSuccess = true;
            } else {
                logStatus = "failed";
            }
        } catch (RuntimeException ex) {
            logStatus = "failed";
            logMessage = ex.getMessage();
        }
        log.info("[定时领料] taskId={} round={} WMS 结果: status={}, msg={}",
                taskId, task.getCurrentRound() + 1, logStatus, logMessage);

        // 事务内更新
        LocalDateTime now = LocalDateTime.now();
        final boolean finalWmsSuccess = wmsSuccess;
        final String finalLogStatus = logStatus;
        final String finalLogMessage = logMessage;
        final String finalWmsTaskNo = wmsTaskNo;
        final String finalRequestId = requestId;
        final BigDecimal[] finalRoundQtyArr = roundQtyArr;

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            int roundNum = task.getCurrentRound() + 1;

            for (int i = 0; i < entries.size(); i++) {
                BigDecimal rq = finalRoundQtyArr[i];
                if (rq.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (!finalWmsSuccess) continue;

                WmsPickTaskEntry e = entries.get(i);
                BigDecimal newSent = safe(e.getSentQty()).add(rq);
                UpdateWrapper<WmsPickTaskEntry> uw = new UpdateWrapper<>();
                uw.eq("id", e.getId())
                  .set("sent_qty", newSent)
                  .set("current_round", e.getCurrentRound() + 1)
                  .set("update_time", now);
                entryMapper.update(null, uw);
            }

            // 写日志
            WmsPickTaskLog taskLog = new WmsPickTaskLog();
            taskLog.setTaskId(task.getId());
            taskLog.setRoundNo(roundNum);
            taskLog.setBillNo(billNo);
            taskLog.setStatus(finalLogStatus);
            taskLog.setWmsTaskNo(finalWmsTaskNo);
            taskLog.setRequestId(finalRequestId);
            taskLog.setMessage(finalLogMessage != null && finalLogMessage.length() > 1000
                    ? finalLogMessage.substring(0, 1000) : finalLogMessage);
            taskLog.setCreateTime(now);
            logMapper.insert(taskLog);

            // 更新任务头
            UpdateWrapper<WmsPickTask> tu = new UpdateWrapper<>();
            tu.eq("id", task.getId())
              .set("current_round", roundNum)
              .set("last_fire_time", now)
              .set("success_count", safeInt(task.getSuccessCount()) + (finalWmsSuccess ? 1 : 0))
              .set("fail_count", safeInt(task.getFailCount()) + (finalWmsSuccess ? 0 : 1))
              .set("message", finalLogMessage != null && finalLogMessage.length() > 500
                    ? finalLogMessage.substring(0, 500) : finalLogMessage)
              .set("update_time", now);
            int intervalMin = task.getIntervalSec() == null ? 1 : task.getIntervalSec();
            tu.set("next_fire_time", now.plusMinutes(intervalMin));
            taskMapper.update(null, tu);
            return null;
        });

        // 再查一次是否全部发满
        List<WmsPickTaskEntry> refreshed = entryMapper.selectList(
                new QueryWrapper<WmsPickTaskEntry>().eq("task_id", taskId));
        boolean allDoneNow = true;
        for (WmsPickTaskEntry e : refreshed) {
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) > 0) { allDoneNow = false; break; }
        }
        if (allDoneNow) { completeTask(task, "所有明细发料已满，自动完成"); return; }

        if (!wmsSuccess) {
            log.warn("[定时领料] taskId={} WMS 推送失败，任务自动停止", taskId);
            failTask(task, finalLogMessage);
            return;
        }

        // 安排下次轮询（快照对比加速：检测到料架有物料消失时 5 秒后再查）
        WmsPickTask latest = taskMapper.selectById(taskId);
        if ("running".equals(latest.getStatus())) {
            String prodNo = latest.getProductionNo() != null ? latest.getProductionNo().toUpperCase() : "";
            boolean snapDisappeared = shelfInventoryCache.isAvailable()
                    && !shelfInventoryCache.getDisappearedMaterials(prodNo).isEmpty();
            if (snapDisappeared) {
                log.info("[定时领料] taskId={} 快照对比检测到料架变化，下一轮 5s 后执行", taskId);
                scheduleNext(latest, 5);
            } else {
                scheduleNext(latest, latest.getNextFireTime());
            }
        }
    }

    private void failTask(WmsPickTask task, String reason) {
        log.info("[定时领料] taskId={} 失败停止: {}", task.getId(), reason);
        UpdateWrapper<WmsPickTask> uw = new UpdateWrapper<>();
        uw.eq("id", task.getId())
          .set("status", "failed")
          .set("message", reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason)
          .set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        ScheduledFuture<?> f = futures.remove(task.getId());
        if (f != null) f.cancel(false);
        runningFlags.remove(task.getId());
    }

    private void completeTask(WmsPickTask task, String reason) {
        log.info("[定时领料] taskId={} 完成: {}", task.getId(), reason);
        UpdateWrapper<WmsPickTask> uw = new UpdateWrapper<>();
        uw.eq("id", task.getId())
          .set("status", "completed")
          .set("message", reason)
          .set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        ScheduledFuture<?> f = futures.remove(task.getId());
        if (f != null) f.cancel(false);
        runningFlags.remove(task.getId());
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private int safeInt(Integer v) { return v != null ? v : 0; }

    // ============ 外部控制方法 ============

    public void startTask(WmsPickTask task) {
        if (!"running".equals(task.getStatus())) {
            task.setStatus("running");
            taskMapper.updateById(task);
        }
        runningFlags.computeIfAbsent(task.getId(), k -> new AtomicBoolean(true)).set(true);
        scheduleNext(task, 0);
    }

    public void pauseTask(long taskId) {
        AtomicBoolean flag = runningFlags.get(taskId);
        if (flag != null) flag.set(false);
        ScheduledFuture<?> f = futures.remove(taskId);
        if (f != null) f.cancel(false);
        UpdateWrapper<WmsPickTask> uw = new UpdateWrapper<>();
        uw.eq("id", taskId).set("status", "paused").set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        log.info("[定时领料] taskId={} 已暂停", taskId);
    }

    public void resumeTask(long taskId) {
        WmsPickTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        task.setStatus("running");
        taskMapper.updateById(task);
        runningFlags.computeIfAbsent(taskId, k -> new AtomicBoolean(true)).set(true);
        scheduleNext(task, 0);
        log.info("[定时领料] taskId={} 已恢复", taskId);
    }

    /** 停掉定时器（不碰 DB）—— forceComplete 和 cancelTask 共用 */
    public void stopTask(long taskId) {
        AtomicBoolean flag = runningFlags.get(taskId);
        if (flag != null) flag.set(false);
        ScheduledFuture<?> f = futures.remove(taskId);
        if (f != null) f.cancel(false);
    }

    public void cancelTask(long taskId) {
        stopTask(taskId);
        UpdateWrapper<WmsPickTask> uw = new UpdateWrapper<>();
        uw.eq("id", taskId).set("status", "cancelled").set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        log.info("[定时领料] taskId={} 已取消", taskId);
    }

    public boolean isEnabled() { return enabled; }

    /** 金蝶分录内码转 int，null/空/非数字 → 0 */
    private int toIntOrZero(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ex) { return 0; }
    }
}
