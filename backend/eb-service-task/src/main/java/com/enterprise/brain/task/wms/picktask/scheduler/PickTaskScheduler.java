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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

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
    private PlatformTransactionManager transactionManager;

    @Value("${pick-task.enabled:false}")
    private boolean enabled;

    @Value("${pick-task.shelf-url:http://192.168.15.20:8081/prod-api/api/upperSystem/shelf/inventory}")
    private String shelfInventoryUrl;

    @Value("${pick-task.shelf-timeout-ms:3000}")
    private int shelfTimeoutMs;

    @Value("${pick-task.max-round-materials:48}")
    private int maxRoundMaterials;

    @Value("${pick-task.relax-threshold:8}")
    private int relaxThreshold;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ScheduledThreadPoolExecutor scheduler;
    private final ConcurrentMap<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    private volatile RestTemplate shelfRestTemplate;

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

    private RestTemplate getShelfRestTemplate() {
        if (shelfRestTemplate == null) {
            synchronized (this) {
                if (shelfRestTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(shelfTimeoutMs);
                    factory.setReadTimeout(shelfTimeoutMs);
                    shelfRestTemplate = new RestTemplate(factory);
                }
            }
        }
        return shelfRestTemplate;
    }

    private void restoreRunningTasks() {
        List<WmsPickTask> running = taskMapper.selectList(
                new QueryWrapper<WmsPickTask>().eq("status", "running"));
        log.info("[定时领料] 启动恢复 running 任务数: {}", running.size());
        for (WmsPickTask task : running) {
            scheduleNext(task, task.getNextFireTime());
        }
    }

    /**
     * 查 WMS 料架库存接口
     * @return Map<productionNo, Map<materialCode, quantityOnShelf>>
     */
    private Map<String, Map<String, BigDecimal>> queryShelfInventory() {
        Map<String, Map<String, BigDecimal>> result = new HashMap<>();
        try {
            ResponseEntity<String> resp = getShelfRestTemplate().getForEntity(shelfInventoryUrl, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[定时领料] 料架接口 HTTP {}，跳过料架驱动", resp.getStatusCode());
                return result;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (root.path("Success").asBoolean() != true) {
                log.warn("[定时领料] 料架接口 Success=false，Message={}", root.path("Message").asText());
                return result;
            }
            JsonNode shelves = root.path("Data").path("Shelves");
            if (!shelves.isArray()) return result;

            for (JsonNode shelf : shelves) {
                JsonNode items = shelf.path("Items");
                if (!items.isArray()) continue;
                for (JsonNode item : items) {
                    String prodNo = item.path("ProdctionNo").asText();
                    String mc = item.path("MaterialCode").asText();
                    BigDecimal qty = new BigDecimal(item.path("Quantity").asInt(0));
                    if (prodNo.isBlank() || mc.isBlank()) continue;

                    result.computeIfAbsent(prodNo, k -> new HashMap<>())
                          .merge(mc, qty, BigDecimal::add);
                }
            }
            log.info("[定时领料] 料架查询成功，涉及生产订单 {} 个", result.size());
        } catch (Exception ex) {
            log.warn("[定时领料] 料架查询失败（{}），跳过料架驱动: {}", shelfInventoryUrl, ex.getMessage());
        }
        return result;
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

        // 查料架
        Map<String, Map<String, BigDecimal>> shelfMap = queryShelfInventory();
        Map<String, BigDecimal> shelfForTask = shelfMap.getOrDefault(task.getProductionNo(), Collections.emptyMap());
        boolean shelfAvailable = !shelfMap.isEmpty();

        boolean relax = remainingCount <= relaxThreshold;
        log.info("[定时领料] taskId={} remainingCount={} relax={} shelfAvailable={}",
                taskId, remainingCount, relax, shelfAvailable);

        // 筛选本轮可推
        BigDecimal[] roundQtyArr = new BigDecimal[entries.size()];
        Arrays.fill(roundQtyArr, BigDecimal.ZERO);

        Map<String, BigDecimal> qtyMap = new LinkedHashMap<>();
        Map<String, String> unitMap = new LinkedHashMap<>();
        Map<String, String> planMap = new LinkedHashMap<>();
        Map<String, String> stationMap = new LinkedHashMap<>();
        Map<String, String> whMap = new LinkedHashMap<>();

        int pickedMaterials = 0;
        for (int i = 0; i < entries.size(); i++) {
            WmsPickTaskEntry e = entries.get(i);
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) <= 0) continue;

            boolean blockByShelf = false;
            if (shelfAvailable && !relax) {
                BigDecimal onShelf = shelfForTask.getOrDefault(e.getMaterialCode(), BigDecimal.ZERO);
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
            qtyMap.put(e.getMaterialCode(), qtyMap.getOrDefault(e.getMaterialCode(), BigDecimal.ZERO).add(roundQty));
            unitMap.putIfAbsent(e.getMaterialCode(), e.getUnitCode());
            planMap.putIfAbsent(e.getMaterialCode(), e.getPlanCode() != null ? e.getPlanCode() : "");
            stationMap.putIfAbsent(e.getMaterialCode(), e.getStationCode());
            whMap.putIfAbsent(e.getMaterialCode(), e.getWarehouseCode());

            pickedMaterials++;
            if (pickedMaterials >= maxRoundMaterials) break;
        }

        // 本轮无物料可推 → 直接安排下次轮询
        if (pickedMaterials == 0) {
            log.info("[定时领料] taskId={} 本轮无可推物料，安排下一次轮询", taskId);
            scheduleNext(task, task.getIntervalSec() == null ? 60 : task.getIntervalSec() * 60);
            return;
        }

        // 组装 WMS JSON
        String billNo = "MES-PTK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();

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
        if (!qtyMap.isEmpty()) {
            wmsEntries = pickingService.fifoSplitWmsEntries(qtyMap, unitMap, planMap, stationMap, whMap);
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
              .set("success_count", task.getSuccessCount() + (finalWmsSuccess ? 1 : 0))
              .set("fail_count", task.getFailCount() + (finalWmsSuccess ? 0 : 1))
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

        // 安排下次轮询
        WmsPickTask latest = taskMapper.selectById(taskId);
        if ("running".equals(latest.getStatus())) {
            scheduleNext(latest, latest.getNextFireTime());
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

    public void cancelTask(long taskId) {
        AtomicBoolean flag = runningFlags.get(taskId);
        if (flag != null) flag.set(false);
        ScheduledFuture<?> f = futures.remove(taskId);
        if (f != null) f.cancel(false);
        UpdateWrapper<WmsPickTask> uw = new UpdateWrapper<>();
        uw.eq("id", taskId).set("status", "cancelled").set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        log.info("[定时领料] taskId={} 已取消", taskId);
    }

    public boolean isEnabled() { return enabled; }
}
