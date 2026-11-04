package com.enterprise.brain.task.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wms.dto.WmsPpbomRowVO;
import com.enterprise.brain.task.wms.dto.WmsPickingSendRequest;
import com.enterprise.brain.task.wms.dto.WmsPickingSendResponse;
import com.enterprise.brain.task.wms.entity.WmsPicking;
import com.enterprise.brain.task.wms.entity.WmsPickingEntry;
import com.enterprise.brain.task.wms.service.WmsPickingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WMS 生产领料申请接口
 */
@RestController
@RequestMapping("/api/v1/wms-picking")
@Tag(name = "WMS 叫料", description = "生产领料申请（ProductionPicking）")
public class WmsPickingController {

    @Resource
    private WmsPickingService wmsPickingService;

    @GetMapping("/ppbom/query")
    @Operation(summary = "按生产订单号查询金蝶生产用料清单",
            description = "调 PRD_PPBOM ExecuteBillQuery，FMOBillNO = productionNo")
    public Result<List<WmsPpbomRowVO>> queryPpbom(
            @RequestParam String productionNo) {
        return Result.success(wmsPickingService.queryPpbomByProductionNo(productionNo, true));
    }

    @PostMapping("/push")
    @Operation(summary = "推送 WMS 领料申请并落库（一步到位）",
            description = "前端组织好物料 + 生产线编码后直接推送 WMS，成功/失败均存本地库")
    public Result<WmsPicking> push(@Valid @RequestBody WmsPickingSendRequest request) {
        return Result.success(wmsPickingService.pushToWms(request));
    }

    @PostMapping("/save")
    @Operation(summary = "保存领料单（pending）", description = "前端挑选的子项物料 + 生产线编码 入库待发送")
    public Result<WmsPicking> save(@Valid @RequestBody WmsPickingSendRequest request) {
        return Result.success(wmsPickingService.savePicking(request));
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "发送 WMS 领料申请", description = "组装 ProductionPicking JSON 发送，依据 Success 更新状态")
    public Result<WmsPicking> send(@PathVariable Long id) {
        return Result.success(wmsPickingService.sendToWms(id));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查领料单历史")
    public Result<Page<WmsPicking>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String lineCode) {
        return Result.success(wmsPickingService.page(pageNum, pageSize, status, keyword, lineCode));
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "查明细行")
    public Result<List<WmsPickingEntry>> entries(@PathVariable Long id) {
        return Result.success(wmsPickingService.listEntries(id));
    }

    @DeleteMapping("/{productionNo}/material/{materialCode}")
    @Operation(summary = "清除某物料的本地累计数据",
            description = "删除该生产订单下指定物料的所有叫料已推记录 + 定时承诺记录，返回清除的明细行数")
    public Result<Integer> clearMaterial(
            @PathVariable String productionNo,
            @PathVariable String materialCode) {
        int cleared = wmsPickingService.clearMaterialSummary(productionNo, materialCode);
        return Result.success(cleared);
    }

    @GetMapping("/{productionNo}/material/{materialCode}/summary")
    @Operation(summary = "查询单物料的本地累计汇总",
            description = "返回 pushedQty（叫料已推）、pickTaskTotalQty（定时承诺总量）、pickTaskSentQty（定时已发）")
    public Result<Map<String, Object>> getMaterialSummary(
            @PathVariable String productionNo,
            @PathVariable String materialCode) {
        WmsPickingService.LocalQtySummary s = wmsPickingService.getLocalQtySummaryForMaterial(productionNo, materialCode);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pushedQty", s.pushedQty);
        data.put("pickTaskTotalQty", s.pickTaskTotalQty);
        data.put("pickTaskSentQty", s.pickTaskSentQty);
        return Result.success(data);
    }
}
