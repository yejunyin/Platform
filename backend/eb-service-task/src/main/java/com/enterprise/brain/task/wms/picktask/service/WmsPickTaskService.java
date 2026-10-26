package com.enterprise.brain.task.wms.picktask.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.wms.picktask.dto.WmsPickTaskCreateRequest;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTask;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskEntry;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskLog;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskEntryMapper;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskLogMapper;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskMapper;
import com.enterprise.brain.task.wms.picktask.scheduler.PickTaskScheduler;
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
 * 定时 WMS 领料任务服务
 */
@Slf4j
@Service
public class WmsPickTaskService {

    @Resource
    private WmsPickTaskMapper taskMapper;

    @Resource
    private WmsPickTaskEntryMapper entryMapper;

    @Resource
    private WmsPickTaskLogMapper logMapper;

    @Resource
    private PickTaskScheduler scheduler;

    @Resource
    private PlatformTransactionManager transactionManager;

    /**
     * 创建定时领料任务（默认 pending 状态，需显式 start 才会开始跑）
     */
    public WmsPickTask create(WmsPickTaskCreateRequest req) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        WmsPickTask created = tx.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            String taskNo = "MES-PTK-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // 取第一个物料行的产品信息作为任务头冗余
            String productCode = "";
            String productName = "";
            if (!req.getEntries().isEmpty()) {
                WmsPickTaskCreateRequest.Entry first = req.getEntries().get(0);
                productCode = first.getProductCode() != null ? first.getProductCode() : "";
                productName = first.getProductName() != null ? first.getProductName() : "";
            }

            // 计算每个明细行的总轮次，取最大作为任务总轮次
            int maxRounds = 0;
            for (WmsPickTaskCreateRequest.Entry e : req.getEntries()) {
                int rounds = e.getTotalQty().divide(e.getPerBoxQty(), 0, RoundingMode.CEILING).intValue();
                if (rounds > maxRounds) maxRounds = rounds;
            }

            WmsPickTask pt = new WmsPickTask();
            pt.setTaskNo(taskNo);
            pt.setProductionNo(req.getProductionNo());
            pt.setProductCode(productCode);
            pt.setProductName(productName);
            pt.setLineCode(req.getLineCode());
            pt.setOrgCode(req.getOrgCode());
            pt.setDeptCode(req.getDeptCode());
            pt.setIntervalSec(req.getIntervalSec());
            // 任务级别的 perBoxQty 用第一条的做参考（主要靠明细行自己的 perBoxQty）
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
            for (WmsPickTaskCreateRequest.Entry e : req.getEntries()) {
                BigDecimal perBox = e.getPerBoxQty();
                int rounds = e.getTotalQty().divide(perBox, 0, RoundingMode.CEILING).intValue();

                WmsPickTaskEntry entry = new WmsPickTaskEntry();
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
                // 默认值统一 ProductionStationCode=3SC01, WarehouseCode=2010（和 WMS 叫料一致）
                entry.setStationCode(e.getProductionStationCode() != null && !e.getProductionStationCode().isEmpty()
                        ? e.getProductionStationCode() : "3SC01");
                entry.setWarehouseCode(e.getOutWarehouseCode() != null && !e.getOutWarehouseCode().isEmpty()
                        ? e.getOutWarehouseCode() : "2010");
                entry.setCreateTime(now);
                entry.setUpdateTime(now);
                entryMapper.insert(entry);
            }

            log.info("[定时领料] 创建任务 taskNo={}, 明细行={}", taskNo, req.getEntries().size());
            return pt;
        });

        // 事务提交后立即推一次
        scheduler.startTask(created);
        return created;
    }

    public WmsPickTask start(Long taskId) {
        WmsPickTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if ("completed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
            throw new BusinessException("任务已结束，不能启动");
        }
        scheduler.startTask(task);
        return taskMapper.selectById(taskId);
    }

    public WmsPickTask pause(Long taskId) {
        WmsPickTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        scheduler.pauseTask(taskId);
        return taskMapper.selectById(taskId);
    }

    public WmsPickTask resume(Long taskId) {
        WmsPickTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        scheduler.resumeTask(taskId);
        return taskMapper.selectById(taskId);
    }

    public WmsPickTask cancel(Long taskId) {
        WmsPickTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        scheduler.cancelTask(taskId);
        return taskMapper.selectById(taskId);
    }

    public Page<WmsPickTask> page(int pageNum, int pageSize, String status, String productionNo) {
        QueryWrapper<WmsPickTask> qw = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        if (productionNo != null && !productionNo.isEmpty()) qw.like("production_no", productionNo);
        qw.orderByDesc("create_time");
        return taskMapper.selectPage(Page.of(pageNum, pageSize), qw);
    }

    public WmsPickTask detail(Long taskId) {
        WmsPickTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        List<WmsPickTaskEntry> entries = entryMapper.selectList(
                new QueryWrapper<WmsPickTaskEntry>().eq("task_id", taskId));
        task.setEntries(entries);
        return task;
    }

    public List<WmsPickTaskEntry> listEntries(Long taskId) {
        return entryMapper.selectList(
                new QueryWrapper<WmsPickTaskEntry>().eq("task_id", taskId).orderByAsc("item_no"));
    }

    public Page<WmsPickTaskLog> pageLogs(Long taskId, int pageNum, int pageSize) {
        QueryWrapper<WmsPickTaskLog> qw = new QueryWrapper<>();
        qw.eq("task_id", taskId).orderByDesc("create_time");
        return logMapper.selectPage(Page.of(pageNum, pageSize), qw);
    }
}
