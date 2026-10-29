package com.enterprise.brain.task.wms.picktask.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.enterprise.brain.task.wms.dto.WmsPickingSendResponse;
import com.enterprise.brain.task.wms.dto.WmsPpbomRowVO;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTask;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskEntry;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskLog;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskEntryMapper;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskLogMapper;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskMapper;
import com.enterprise.brain.task.service.WmsService;
import com.enterprise.brain.task.wms.service.WmsPickingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时领料任务调度器
 *
 * 启动时自动恢复所有 status=running 的任务；
 * 每个任务推完一轮后根据 intervalSec 安排下一次，直到所有明细行发满自动 completed。
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 调度线程池（核心线程数 = 4，足够并发多个任务） */
    private ScheduledThreadPoolExecutor scheduler;

    /** taskId → Future 映射，支持取消/暂停 */
    private final ConcurrentMap<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /** taskId → 运行中标记（防止重复 schedule） */
    private final ConcurrentMap<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.warn("[定时领料] pick-task.enabled=false，调度器已禁用，不恢复 running 任务");
            return;
        }
        scheduler = new ScheduledThreadPoolExecutor(4, r -> {
            Thread t = new Thread(r, "PickTaskScheduler");
            t.setDaemon(true);
            return t;
        });
        // 优雅拒绝：队列满时，最旧的任务会被拒绝，交由 DB 恢复
        scheduler.setRemoveOnCancelPolicy(true);

        // 启动时恢复所有 running 状态的任务
        restoreRunningTasks();
    }

    private void restoreRunningTasks() {
        List<WmsPickTask> running = taskMapper.selectList(
                new QueryWrapper<WmsPickTask>().eq("status", "running"));
        log.info("[定时领料] 启动恢复 running 任务数: {}", running.size());
        for (WmsPickTask task : running) {
            // 严格按 DB 里存的 nextFireTime 计算 delay；过期则立即补跑
            scheduleNext(task, task.getNextFireTime());
        }
    }

    /**
     * 安排下次执行（首次 delay=0 立即执行；后续按 intervalSec 延迟）
     */
    public void scheduleNext(WmsPickTask task, long delaySec) {
        if (!enabled) {
            log.warn("[定时领料] pick-task.enabled=false，忽略 schedule 请求 taskId={}", task.getId());
            return;
        }
        if (scheduler == null) {
            log.warn("[定时领料] scheduler 未初始化（可能 enabled=false），忽略 schedule taskId={}", task.getId());
            return;
        }
        long taskId = task.getId();
        AtomicBoolean running = runningFlags.computeIfAbsent(taskId, k -> new AtomicBoolean(true));
        if (!running.get()) {
            log.info("[定时领料] taskId={} 已暂停/取消，跳过 schedule", taskId);
            return;
        }

        // 防止重复 schedule：如果已有 Future 且未完成，先 cancel
        ScheduledFuture<?> oldFuture = futures.get(taskId);
        if (oldFuture != null && !oldFuture.isDone()) {
            oldFuture.cancel(false);
        }

        long finalDelay = delaySec;
        ScheduledFuture<?> future = scheduler.schedule(() -> executeOneRound(taskId),
                finalDelay, TimeUnit.SECONDS);
        futures.put(taskId, future);
        log.info("[定时领料] taskId={} 已 schedule，delay={}s", taskId, finalDelay);
    }

    /**
     * 按 DB 中存的 nextFireTime 精确计算 delay，保证严格按时间点触发
     * 如果 nextFireTime 已过期（如服务重启期间错过），delay=0 立即补一次
     */
    public void scheduleNext(WmsPickTask task, LocalDateTime nextFireTime) {
        long delaySec;
        if (nextFireTime == null) {
            delaySec = 0;
        } else {
            delaySec = Math.max(0, Duration.between(LocalDateTime.now(), nextFireTime).getSeconds());
        }
        scheduleNext(task, delaySec);
    }

    /**
     * 执行一轮：读最新 DB 状态 → 组装 WMS JSON → 推 → 存日志 → 判断是否继续
     */
    private void executeOneRound(long taskId) {
        log.info("[定时领料] ===== 开始执行 taskId={} 一轮 =====", taskId);

        WmsPickTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[定时领料] taskId={} 不存在，跳过", taskId);
            runningFlags.remove(taskId);
            futures.remove(taskId);
            return;
        }

        AtomicBoolean running = runningFlags.get(taskId);
        if (running == null || !running.get()) {
            log.info("[定时领料] taskId={} 已暂停/取消，本轮跳过", taskId);
            return;
        }

        if (!"running".equals(task.getStatus())) {
            log.info("[定时领料] taskId={} status={}，不执行", taskId, task.getStatus());
            runningFlags.remove(taskId);
            futures.remove(taskId);
            return;
        }

        // 读最新明细
        List<WmsPickTaskEntry> entries = entryMapper.selectList(
                new QueryWrapper<WmsPickTaskEntry>().eq("task_id", taskId));

        // 检查是否所有明细都已发满（内部检查）
        boolean allDone = true;
        for (WmsPickTaskEntry e : entries) {
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) > 0) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            completeTask(task, "所有明细发料已满");
            return;
        }

        // ============ 全局竞态防护：查叫料已推送 + 金蝶应发，限制本轮不超全局剩余 ============
        // 一次查询同时拿到金蝶应发数量和本地累计（pushedQty + pickTaskSentQty 已填充）
        List<WmsPpbomRowVO> ppbomList = pickingService.queryPpbomByProductionNo(task.getProductionNo(), false);
        Map<String, WmsPpbomRowVO> ppbomMap = new HashMap<>();
        for (WmsPpbomRowVO vo : ppbomList) {
            ppbomMap.put(vo.getMaterialCode(), vo);
        }

        // 本轮 WMS 单号
        String billNo = "MES-PTK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();

        // 组装 ProductionPicking JSON
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
        List<BigDecimal> roundQtyList = new ArrayList<>(); // 本轮各明细的实际发料量

        for (WmsPickTaskEntry e : entries) {
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) <= 0) {
                // 内部已发满，本轮跳过
                roundQtyList.add(BigDecimal.ZERO);
                continue;
            }

            // 全局剩余防护：不能超过金蝶应发数量 - 叫料已推 - 其他定时任务承诺总量（预占）
            // 注意：pickTaskTotalQty 包含了"当前任务自己"承诺的 totalQty（entries 已在 DB 里），
            //       需要扣掉自己，否则第一轮就会被自己卡死（自己预占了自己要发的量）
            BigDecimal globalLeft = null;
            WmsPpbomRowVO vo = ppbomMap.get(e.getMaterialCode());
            if (vo != null && vo.getNeedQty() != null) {
                BigDecimal pushed = safe(vo.getPushedQty());
                BigDecimal ptTotal = safe(vo.getPickTaskTotalQty());
                // 扣掉当前 entry 自己承诺的 totalQty
                BigDecimal ownTotal = safe(e.getTotalQty());
                BigDecimal otherOccupied = pushed.add(ptTotal).subtract(ownTotal);
                if (otherOccupied.compareTo(BigDecimal.ZERO) < 0) {
                    otherOccupied = BigDecimal.ZERO;
                }
                globalLeft = vo.getNeedQty().subtract(otherOccupied);
                if (globalLeft.compareTo(BigDecimal.ZERO) < 0) {
                    globalLeft = BigDecimal.ZERO;
                }
            }

            // 取三者最小：内部剩余、每箱数量、全局剩余
            BigDecimal roundQty = left.min(e.getPerBoxQty());
            if (globalLeft != null) {
                roundQty = roundQty.min(globalLeft);
            }
            if (roundQty.compareTo(BigDecimal.ZERO) <= 0) {
                // 全局剩余已用完（可能被叫料超了），跳过本轮
                roundQtyList.add(BigDecimal.ZERO);
                continue;
            }
            roundQtyList.add(roundQty);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ItemNo", itemNo++);
            m.put("MaterialCode", e.getMaterialCode());
            m.put("BatchCode", null);
            m.put("Quantity", roundQty);
            m.put("UnitCode", e.getUnitCode());
            m.put("PlanCode", e.getPlanCode() != null ? e.getPlanCode() : "");
            m.put("ProductionStationCode", e.getStationCode());
            m.put("OutWarehouseCode", e.getWarehouseCode());
            wmsEntries.add(m);
        }
        data.put("Entries", wmsEntries);

        // 全局剩余已用完，所有物料都被跳过
        if (wmsEntries.isEmpty()) {
            if (task.getCurrentRound() == null || task.getCurrentRound() == 0) {
                // 第一轮就空 → 定时任务本身还没发出去过，但叫料/其他定时任务已经把额度占满了
                String reason = "第一次调度即无可用额度：叫料已推 + 其他定时任务承诺已将金蝶应发数量占满，请检查该生产订单的全局剩余";
                log.warn("[定时领料] taskId={} 第一轮即全局剩余为0，任务置失败: {}", taskId, reason);
                failTask(task, reason);
            } else {
                completeTask(task, "全局剩余已用完（可能被叫料/其他定时任务抢占），自动停止");
            }
            return;
        }

        log.info("[定时领料] taskId={} round={} billNo={} 物料行数={}",
                taskId, task.getCurrentRound() + 1, billNo, wmsEntries.size());
        try {
            log.info("[定时领料] ====== 推送 WMS ProductionPicking JSON ======\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (Exception ignored) {}

        // 推 WMS
        String logStatus;
        String logMessage;
        String wmsTaskNo = null;
        String requestId = null;
        boolean wmsSuccess = false;
        try {
            WmsPickingSendResponse resp = wmsService.sendProductionPicking(data, task.getLineCode());
            logMessage = resp.getMessage();
            if (resp.getData() != null) {
                wmsTaskNo = resp.getData().getWmsTaskNo();
                requestId = resp.getData().getRequestId();
            }
            if (Boolean.TRUE.equals(resp.getSuccess())) {
                logStatus = "sent";
                wmsSuccess = true;
            } else {
                logStatus = "failed";
            }
        } catch (RuntimeException ex) {
            logStatus = "failed";
            logMessage = ex.getMessage();
        }
        log.info("[定时领料] taskId={} round={} WMS 推送结果: status={}, msg={}",
                taskId, task.getCurrentRound() + 1, logStatus, logMessage);

        // 事务内：更新明细 + 任务头 + 写日志
        LocalDateTime now = LocalDateTime.now();
        WmsPickTask finalTask = task;
        boolean finalWmsSuccess = wmsSuccess;
        String finalLogStatus = logStatus;
        String finalLogMessage = logMessage;
        String finalWmsTaskNo = wmsTaskNo;
        String finalRequestId = requestId;

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            // 更新明细行的 sentQty / currentRound
            int idx = 0;
            int roundNum = finalTask.getCurrentRound() + 1;
            for (int i = 0; i < entries.size(); i++) {
                WmsPickTaskEntry e = entries.get(i);
                BigDecimal roundQty = roundQtyList.get(i);
                if (roundQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                // 只有 WMS 推送成功才累加 sentQty
                if (finalWmsSuccess) {
                    BigDecimal newSent = safe(e.getSentQty()).add(roundQty);
                    UpdateWrapper<WmsPickTaskEntry> uw = new UpdateWrapper<>();
                    uw.eq("id", e.getId())
                      .set("sent_qty", newSent)
                      .set("current_round", e.getCurrentRound() + 1)
                      .set("update_time", now);
                    entryMapper.update(null, uw);
                }
            }

            // 写日志
            WmsPickTaskLog taskLog = new WmsPickTaskLog();
            taskLog.setTaskId(finalTask.getId());
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
            tu.eq("id", finalTask.getId())
              .set("current_round", roundNum)
              .set("last_fire_time", now)
              .set("success_count", finalTask.getSuccessCount() + (finalWmsSuccess ? 1 : 0))
              .set("fail_count", finalTask.getFailCount() + (finalWmsSuccess ? 0 : 1))
              .set("message", finalLogMessage != null && finalLogMessage.length() > 500
                    ? finalLogMessage.substring(0, 500) : finalLogMessage)
              .set("update_time", now);

            // 更新 nextFireTime
            int intervalMin = finalTask.getIntervalSec() == null ? 1 : finalTask.getIntervalSec();
            tu.set("next_fire_time", now.plusMinutes(intervalMin));

            taskMapper.update(null, tu);
            return null;
        });

        // 检查是否所有明细都发满了 → 完成
        List<WmsPickTaskEntry> refreshedEntries = entryMapper.selectList(
                new QueryWrapper<WmsPickTaskEntry>().eq("task_id", taskId));
        boolean allDoneNow = true;
        for (WmsPickTaskEntry e : refreshedEntries) {
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) > 0) {
                allDoneNow = false;
                break;
            }
        }

        if (allDoneNow) {
            completeTask(task, "所有明细发料已满，自动完成");
            return;
        }

        // 推送失败 → 任务置 failed 停止
        if (!wmsSuccess) {
            log.warn("[定时领料] taskId={} 本轮 WMS 推送失败，任务自动停止", taskId);
            failTask(task, finalLogMessage);
            return;
        }

        // 推送成功 → 按 DB 里存的 nextFireTime 精确安排下一轮
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

        // 取消 future
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

        // 取消 future
        ScheduledFuture<?> f = futures.remove(task.getId());
        if (f != null) f.cancel(false);
        runningFlags.remove(task.getId());
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ============ 外部控制方法 ============

    public void startTask(WmsPickTask task) {
        // 如果 status 不是 running，先更新 DB
        if (!"running".equals(task.getStatus())) {
            task.setStatus("running");
            taskMapper.updateById(task);
        }
        runningFlags.computeIfAbsent(task.getId(), k -> new AtomicBoolean(true)).set(true);
        // 立即推第一次（创建/启动时刻触发），推完后 executeOneRound 会自动按 interval 更新 nextFireTime
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
        // 恢复后立即推一次，推完后按 interval 自动安排后续
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

    /** 定时领料开关；pick-task.enabled=false 时所有调度行为被禁用 */
    public boolean isEnabled() {
        return enabled;
    }
}
