package com.enterprise.brain.task.wms.oneclickpick.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.wms.oneclickpick.dto.WmsOneClickPickCreateRequest;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPick;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPickEntry;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPickLog;
import com.enterprise.brain.task.wms.oneclickpick.mapper.WmsOneClickPickEntryMapper;
import com.enterprise.brain.task.wms.oneclickpick.mapper.WmsOneClickPickLogMapper;
import com.enterprise.brain.task.wms.oneclickpick.mapper.WmsOneClickPickMapper;
import com.enterprise.brain.task.wms.oneclickpick.scheduler.OneClickPickScheduler;
import com.enterprise.brain.task.wms.service.WmsPickingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 一键领料任务服务
 */
@Slf4j
@Service
public class WmsOneClickPickService {

    @Resource
    private WmsOneClickPickMapper taskMapper;

    @Resource
    private WmsOneClickPickEntryMapper entryMapper;

    @Resource
    private WmsOneClickPickLogMapper logMapper;

    @Resource
    private OneClickPickScheduler scheduler;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Resource
    private WmsPickingService pickingService;

    /**
     * 创建一键领料任务（默认 pending 状态，需显式 start 才会开始跑）
     */
    public WmsOneClickPick create(WmsOneClickPickCreateRequest req) {
        if (!scheduler.isEnabled()) {
            throw new BusinessException("一键领料任务已关闭（one-click-pick.enabled=false），请先在 application.yml 开启");
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        WmsOneClickPick created = tx.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            String taskNo = "MES-OCP-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // 取第一个物料行的产品信息作为任务头冗余
            String productCode = "";
            String productName = "";
            if (!req.getEntries().isEmpty()) {
                WmsOneClickPickCreateRequest.Entry first = req.getEntries().get(0);
                productCode = first.getProductCode() != null ? first.getProductCode() : "";
                productName = first.getProductName() != null ? first.getProductName() : "";
            }

            // 计算每个明细行的总轮次，取最大作为任务总轮次
            int maxRounds = 0;
            for (WmsOneClickPickCreateRequest.Entry e : req.getEntries()) {
                int rounds = e.getTotalQty().divide(e.getPerBoxQty(), 0, RoundingMode.CEILING).intValue();
                if (rounds > maxRounds) maxRounds = rounds;
            }

            WmsOneClickPick pt = new WmsOneClickPick();
            pt.setTaskNo(taskNo);
            pt.setProductionNo(req.getProductionNo());
            pt.setProductCode(productCode);
            pt.setProductName(productName);
            pt.setLineCode(req.getLineCode());
            pt.setOrgCode(req.getOrgCode());
            pt.setDeptCode(req.getDeptCode());
            pt.setIntervalSec(req.getIntervalSec());
            pt.setPerBoxQty(req.getEntries().get(0).getPerBoxQty());
            pt.setStatus("running");  // 创建即运行，立即推第一次
            pt.setTotalRounds(maxRounds);
            pt.setCurrentRound(0);
            pt.setSuccessCount(0);
            pt.setFailCount(0);
            pt.setCreateTime(now);
            pt.setUpdateTime(now);
            taskMapper.insert(pt);

            int itemNo = 1;
            for (WmsOneClickPickCreateRequest.Entry e : req.getEntries()) {
                BigDecimal perBox = e.getPerBoxQty();
                int rounds = e.getTotalQty().divide(perBox, 0, RoundingMode.CEILING).intValue();

                WmsOneClickPickEntry entry = new WmsOneClickPickEntry();
                entry.setTaskId(pt.getId());
                entry.setItemNo(itemNo++);
                entry.setMaterialCode(e.getMaterialCode());
                entry.setMaterialName(e.getMaterialName());
                entry.setPlanQty(e.getPlanQty());
                entry.setTotalQty(e.getTotalQty());
                entry.setPerBoxQty(perBox);
                entry.setTotalRounds(rounds);
                entry.setCurrentRound(0);
                entry.setSentQty(BigDecimal.ZERO);
                entry.setUnitCode(e.getUnitCode());
                entry.setPlanCode(e.getPlanCode() != null ? e.getPlanCode() : "");
                entry.setStationCode(e.getProductionStationCode() != null && !e.getProductionStationCode().isEmpty()
                        ? e.getProductionStationCode() : "3SC01");
                entry.setWarehouseCode(e.getOutWarehouseCode() != null && !e.getOutWarehouseCode().isEmpty()
                        ? e.getOutWarehouseCode() : "2010");
                entry.setCreateTime(now);
                entry.setUpdateTime(now);
                entryMapper.insert(entry);
            }

            log.info("[一键领料] 创建任务 taskNo={}, 明细行={}", taskNo, req.getEntries().size());
            return pt;
        });

        // 事务提交后立即推一次
        scheduler.startTask(created);
        return created;
    }

    public WmsOneClickPick start(Long taskId) {
        if (!scheduler.isEnabled()) {
            throw new BusinessException("一键领料任务已关闭（one-click-pick.enabled=false），请先在 application.yml 开启");
        }
        WmsOneClickPick task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if ("completed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
            throw new BusinessException("任务已结束，不能启动");
        }
        scheduler.startTask(task);
        return taskMapper.selectById(taskId);
    }

    public WmsOneClickPick pause(Long taskId) {
        WmsOneClickPick task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        scheduler.pauseTask(taskId);
        return taskMapper.selectById(taskId);
    }

    public WmsOneClickPick resume(Long taskId) {
        if (!scheduler.isEnabled()) {
            throw new BusinessException("一键领料任务已关闭（one-click-pick.enabled=false），请先在 application.yml 开启");
        }
        WmsOneClickPick task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        scheduler.resumeTask(taskId);
        return taskMapper.selectById(taskId);
    }

    public WmsOneClickPick cancel(Long taskId) {
        WmsOneClickPick task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        scheduler.cancelTask(taskId);
        return taskMapper.selectById(taskId);
    }

    /**
     * 强制终止任务：停止定时器 + 按"轮次 × 单次数量"核算每个明细行的已发数量。
     *  - 如果 entry 已发满（sentQty >= totalQty）则跳过不计算
     *  - 计算值上限 totalQty
     *  - 任务头状态置为 completed（保留在 DB，继续计入"已有一键承诺"）
     */
    public WmsOneClickPick forceComplete(Long taskId) {
        WmsOneClickPick task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if ("completed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
            throw new BusinessException("任务已结束，不能终止");
        }

        // 1. 停掉定时调度
        scheduler.stopTask(taskId);

        // 2. 事务内：更新 entries.sent_qty + 任务头 → completed
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            List<WmsOneClickPickEntry> entries = entryMapper.selectList(
                    new QueryWrapper<WmsOneClickPickEntry>().eq("task_id", taskId));

            int adjustedCount = 0;
            int alreadyFullCount = 0;
            BigDecimal totalAdjustQty = BigDecimal.ZERO;

            for (WmsOneClickPickEntry e : entries) {
                BigDecimal sent = e.getSentQty() != null ? e.getSentQty() : BigDecimal.ZERO;
                BigDecimal total = e.getTotalQty() != null ? e.getTotalQty() : BigDecimal.ZERO;
                BigDecimal perBox = e.getPerBoxQty() != null ? e.getPerBoxQty() : BigDecimal.ZERO;
                int curRound = e.getCurrentRound() == null ? 0 : e.getCurrentRound();

                // 已发满 → 跳过
                if (sent.compareTo(total) >= 0) {
                    alreadyFullCount++;
                    continue;
                }

                // 按公式核算：轮次 × 单次数量，上限 totalQty
                BigDecimal calc = perBox.multiply(BigDecimal.valueOf(curRound));
                if (calc.compareTo(total) > 0) calc = total;

                if (calc.compareTo(sent) != 0) {
                    UpdateWrapper<WmsOneClickPickEntry> uw = new UpdateWrapper<>();
                    uw.eq("id", e.getId())
                      .set("sent_qty", calc)
                      .set("update_time", now);
                    entryMapper.update(null, uw);
                    totalAdjustQty = totalAdjustQty.add(calc.subtract(sent));
                    adjustedCount++;
                    log.info("[一键领料·终止] taskId={} entryId={} material={} sentQty {} → {} (round={}, perBox={})",
                            taskId, e.getId(), e.getMaterialCode(), sent, calc, curRound, perBox);
                }
            }

            // 更新任务头
            UpdateWrapper<WmsOneClickPick> uw = new UpdateWrapper<>();
            uw.eq("id", taskId)
              .set("status", "completed")
              .set("message", "用户手动终止：已按轮次核算已发数量")
              .set("update_time", now);
            taskMapper.update(null, uw);

            log.info("[一键领料·终止] taskId={} 已终止，明细行={}，已发满跳过={}，调整={}行，净增量={}",
                    taskId, entries.size(), alreadyFullCount, adjustedCount, totalAdjustQty);
            return null;
        });

        return taskMapper.selectById(taskId);
    }

    public Page<WmsOneClickPick> page(int pageNum, int pageSize, String status, String productionNo, String keyword, String lineCode) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        QueryWrapper<WmsOneClickPick> qw = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        if (productionNo != null && !productionNo.isEmpty()) qw.like("production_no", productionNo);
        if (lineCode != null && !lineCode.isEmpty()) qw.eq("line_code", lineCode);
        qw.ge("create_time", weekAgo);
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        if (kw != null) {
            qw.and(w -> w.like("task_no", kw).or().like("production_no", kw));
        }
        qw.orderByDesc("create_time");
        return taskMapper.selectPage(Page.of(pageNum, pageSize), qw);
    }

    public WmsOneClickPick detail(Long taskId) {
        WmsOneClickPick task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        List<WmsOneClickPickEntry> entries = entryMapper.selectList(
                new QueryWrapper<WmsOneClickPickEntry>().eq("task_id", taskId));
        task.setEntries(entries);
        return task;
    }

    public List<WmsOneClickPickEntry> listEntries(Long taskId) {
        return entryMapper.selectList(
                new QueryWrapper<WmsOneClickPickEntry>().eq("task_id", taskId).orderByAsc("item_no"));
    }

    public Page<WmsOneClickPickLog> pageLogs(Long taskId, int pageNum, int pageSize) {
        QueryWrapper<WmsOneClickPickLog> qw = new QueryWrapper<>();
        qw.eq("task_id", taskId).orderByDesc("create_time");
        return logMapper.selectPage(Page.of(pageNum, pageSize), qw);
    }
}
