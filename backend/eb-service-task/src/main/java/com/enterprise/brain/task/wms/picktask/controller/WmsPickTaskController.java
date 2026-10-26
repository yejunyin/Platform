package com.enterprise.brain.task.wms.picktask.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wms.dto.WmsPpbomRowVO;
import com.enterprise.brain.task.wms.picktask.dto.WmsPickTaskCreateRequest;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTask;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskEntry;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskLog;
import com.enterprise.brain.task.wms.picktask.service.WmsPickTaskService;
import com.enterprise.brain.task.wms.service.WmsPickingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时 WMS 领料任务 REST 接口
 */
@RestController
@RequestMapping("/api/v1/wms-pick-task")
@Tag(name = "定时 WMS 领料任务", description = "按间隔时间循环推送 WMS ProductionPicking，发满自动停止")
public class WmsPickTaskController {

    @Resource
    private WmsPickTaskService pickTaskService;

    @Resource
    private WmsPickingService pickingService;

    /** 复用 WMS 叫料的 PRD_PPBOM 查询接口 */
    @GetMapping("/ppbom/query")
    @Operation(summary = "按生产订单号查询金蝶生产用料清单")
    public Result<List<WmsPpbomRowVO>> queryPpbom(@RequestParam String productionNo) {
        return Result.success(pickingService.queryPpbomByProductionNo(productionNo));
    }

    @PostMapping
    @Operation(summary = "创建定时领料任务", description = "默认 pending 状态，需显式 /start 才开始跑")
    public Result<WmsPickTask> create(@Valid @RequestBody WmsPickTaskCreateRequest request) {
        return Result.success(pickTaskService.create(request));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "启动定时任务（开始循环推 WMS）")
    public Result<WmsPickTask> start(@PathVariable Long id) {
        return Result.success(pickTaskService.start(id));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "暂停定时任务")
    public Result<WmsPickTask> pause(@PathVariable Long id) {
        return Result.success(pickTaskService.pause(id));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "恢复定时任务")
    public Result<WmsPickTask> resume(@PathVariable Long id) {
        return Result.success(pickTaskService.resume(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消定时任务（不可恢复）")
    public Result<WmsPickTask> cancel(@PathVariable Long id) {
        return Result.success(pickTaskService.cancel(id));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查定时任务列表")
    public Result<Page<WmsPickTask>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productionNo) {
        return Result.success(pickTaskService.page(pageNum, pageSize, status, productionNo));
    }

    @GetMapping("/{id}")
    @Operation(summary = "任务详情（含明细）")
    public Result<WmsPickTask> detail(@PathVariable Long id) {
        return Result.success(pickTaskService.detail(id));
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "任务明细行")
    public Result<List<WmsPickTaskEntry>> entries(@PathVariable Long id) {
        return Result.success(pickTaskService.listEntries(id));
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "任务执行日志分页")
    public Result<Page<WmsPickTaskLog>> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(pickTaskService.pageLogs(id, pageNum, pageSize));
    }
}
