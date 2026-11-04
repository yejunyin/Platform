package com.enterprise.brain.task.wms.oneclickpick.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wms.dto.WmsPpbomRowVO;
import com.enterprise.brain.task.wms.oneclickpick.dto.WmsOneClickPickCreateRequest;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPick;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPickEntry;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPickLog;
import com.enterprise.brain.task.wms.oneclickpick.service.WmsOneClickPickService;
import com.enterprise.brain.task.wms.service.WmsPickingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 一键领料 REST 接口
 */
@RestController
@RequestMapping("/api/v1/wms-one-click-pick")
@Tag(name = "一键领料", description = "按间隔时间循环推送 WMS ProductionPicking，发满自动停止")
public class WmsOneClickPickController {

    @Resource
    private WmsOneClickPickService oneClickPickService;

    @Resource
    private WmsPickingService pickingService;

    /** 复用 WMS 叫料的 PRD_PPBOM 查询接口 */
    @GetMapping("/ppbom/query")
    @Operation(summary = "按生产订单号查询金蝶生产用料清单")
    public Result<List<WmsPpbomRowVO>> queryPpbom(@RequestParam String productionNo) {
        return Result.success(pickingService.queryPpbomByProductionNo(productionNo, true));
    }

    @PostMapping
    @Operation(summary = "创建一键领料任务")
    public Result<WmsOneClickPick> create(@Valid @RequestBody WmsOneClickPickCreateRequest request) {
        return Result.success(oneClickPickService.create(request));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "启动一键领料任务（开始循环推 WMS）")
    public Result<WmsOneClickPick> start(@PathVariable Long id) {
        return Result.success(oneClickPickService.start(id));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "暂停一键领料任务")
    public Result<WmsOneClickPick> pause(@PathVariable Long id) {
        return Result.success(oneClickPickService.pause(id));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "恢复一键领料任务")
    public Result<WmsOneClickPick> resume(@PathVariable Long id) {
        return Result.success(oneClickPickService.resume(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消一键领料任务（不可恢复）")
    public Result<WmsOneClickPick> cancel(@PathVariable Long id) {
        return Result.success(oneClickPickService.cancel(id));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查一键领料任务列表")
    public Result<Page<WmsOneClickPick>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productionNo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String lineCode) {
        return Result.success(oneClickPickService.page(pageNum, pageSize, status, productionNo, keyword, lineCode));
    }

    @GetMapping("/{id}")
    @Operation(summary = "任务详情（含明细）")
    public Result<WmsOneClickPick> detail(@PathVariable Long id) {
        return Result.success(oneClickPickService.detail(id));
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "任务明细行")
    public Result<List<WmsOneClickPickEntry>> entries(@PathVariable Long id) {
        return Result.success(oneClickPickService.listEntries(id));
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "任务执行日志分页")
    public Result<Page<WmsOneClickPickLog>> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(oneClickPickService.pageLogs(id, pageNum, pageSize));
    }
}
