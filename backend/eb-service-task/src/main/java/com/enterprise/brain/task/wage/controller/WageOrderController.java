package com.enterprise.brain.task.wage.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wage.dto.PageVO;
import com.enterprise.brain.task.wage.dto.WageOrderDoneRequest;
import com.enterprise.brain.task.wage.dto.WageOrderStartRequest;
import com.enterprise.brain.task.wage.dto.WageOrderVO;
import com.enterprise.brain.task.wage.dto.WagePpbomRowVO;
import com.enterprise.brain.task.wage.entity.WageMaterial;
import com.enterprise.brain.task.wage.service.KingdeeWageService;
import com.enterprise.brain.task.wage.service.WageOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 工资核算-生产任务单（单据头）+ 生产用料清单（明细级业务状态）
 */
@RestController
@RequestMapping("/api/v1/wage/orders")
@Tag(name = "工资核算-生产任务单")
public class WageOrderController {

    @Resource
    private WageOrderService orderService;

    @Resource
    private KingdeeWageService kingdeeWageService;

    /** 任务单分页（单据头级别） */
    @GetMapping("/page")
    public Result<PageVO<WageOrderVO>> page(@RequestParam(required = false) String workshop,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(orderService.page(workshop, keyword, pageNum, pageSize));
    }

    /** 用料清单明细（带加工组名称 + 完整业务状态） */
    @GetMapping("/materials")
    public Result<List<WageMaterial>> materials(@RequestParam(required = false) String taskNo,
                                                @RequestParam(required = false) String bizStatus,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(orderService.materials(taskNo, bizStatus, keyword));
    }

    @GetMapping("/task-nos")
    public Result<List<String>> taskNos() {
        return Result.success(orderService.taskNoOptions());
    }

    @GetMapping("/workshops")
    public Result<List<String>> workshops() {
        return Result.success(orderService.workshopOptions());
    }

    /** 从 MES 同步近一年生产订单 */
    @PostMapping("/sync")
    public Result<Map<String, Integer>> sync() {
        int added = orderService.syncFromMes();
        return Result.success("同步完成", Map.of("added", added));
    }

    /** 从 wage_material 反推工单头到 wage_order */
    @PostMapping("/sync-from-materials")
    @Operation(summary = "从用料清单反推任务单头",
            description = "取 wage_material 所有 task_no，去金蝶 PRD_MO 查生产订单后写入 wage_order")
    public Result<Map<String, Integer>> syncFromMaterials() {
        int added = orderService.syncFromMaterials();
        return Result.success("同步完成", Map.of("added", added));
    }

    // ========================================
    // 明细级操作（按 wage_material.id）
    // ========================================

    /** 开工提交（按明细 ID） */
    @PostMapping("/materials/{materialId}/start")
    public Result<Boolean> startMaterial(@PathVariable Long materialId,
                                          @Valid @RequestBody WageOrderStartRequest request) {
        orderService.startMaterial(materialId, request);
        return Result.success("开工提交成功", Boolean.TRUE);
    }

    /** 确认完工（按明细 ID） */
    @PostMapping("/materials/{materialId}/done")
    public Result<Boolean> doneMaterial(@PathVariable Long materialId,
                                         @RequestBody WageOrderDoneRequest request) {
        orderService.doneMaterial(materialId, request);
        return Result.success("已确认完工", Boolean.TRUE);
    }

    /** 修改加工组别（按明细 ID） */
    @PutMapping("/materials/{materialId}/group")
    public Result<Boolean> changeGroupMaterial(@PathVariable Long materialId,
                                                @RequestBody Map<String, Long> body) {
        orderService.changeGroupMaterial(materialId, body.get("groupId"));
        return Result.success("修改成功", Boolean.TRUE);
    }

    /** 删除明细 */
    @DeleteMapping("/materials/{materialId}")
    public Result<Boolean> deleteMaterial(@PathVariable Long materialId) {
        orderService.removeMaterial(materialId);
        return Result.success("删除成功", Boolean.TRUE);
    }

    // ========================================
    // 兼容：按单据头 ID 批量操作（作用于该 task_no 下所有符合状态的明细）
    // ========================================

    /** 兼容：单据头级开工（批量该单下所有"计划"明细） */
    @Deprecated
    @PostMapping("/{id}/start")
    public Result<Boolean> start(@PathVariable Long id, @Valid @RequestBody WageOrderStartRequest request) {
        orderService.start(id, request);
        return Result.success("开工提交成功", Boolean.TRUE);
    }

    /** 兼容：单据头级完工（批量该单下所有"开工"明细） */
    @Deprecated
    @PostMapping("/{id}/done")
    public Result<Boolean> done(@PathVariable Long id, @RequestBody WageOrderDoneRequest request) {
        orderService.done(id, request);
        return Result.success("已确认完工", Boolean.TRUE);
    }

    /** 兼容：单据头级改组别（批量该单下所有未完工明细） */
    @Deprecated
    @PutMapping("/{id}/group")
    public Result<Boolean> changeGroup(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        orderService.changeGroup(id, body.get("groupId"));
        return Result.success("修改成功", Boolean.TRUE);
    }

    /** 兼容：删除整个单据（连带子表） */
    @Deprecated
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        orderService.remove(id);
        return Result.success("删除成功", Boolean.TRUE);
    }

    // ========================================
    // 金蝶 PRD_PPBOM 查询 & 保存
    // ========================================

    @GetMapping("/ppbom/query")
    @Operation(summary = "查询金蝶《生产用料清单》PRD_PPBOM",
            description = "子项物料名称包含关键词 + 创建日期范围")
    public Result<List<WagePpbomRowVO>> queryPpbom(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String startDate,
                                                    @RequestParam(required = false) Integer limit) {
        java.util.List<String> keywords = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            keywords.addAll(Arrays.asList(keyword.split("[,，;；]")));
            keywords.removeIf(String::isEmpty);
        }
        return Result.success(kingdeeWageService.queryPpbomByKeyword(keywords, startDate, limit));
    }

    @PostMapping("/ppbom/save")
    @Operation(summary = "保存金蝶用料清单到本地",
            description = "按 task_no + material_code 幂等写入 wage_material")
    public Result<Map<String, Integer>> savePpbom(@RequestBody List<WagePpbomRowVO> rows) {
        int added = orderService.savePpbomRows(rows);
        return Result.success(Map.of("added", added, "received", rows == null ? 0 : rows.size()));
    }
}
