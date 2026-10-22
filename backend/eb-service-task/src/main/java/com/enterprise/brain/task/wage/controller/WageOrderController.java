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
 * 工资核算-生产任务单 + 用料清单
 */
@RestController
@RequestMapping("/api/v1/wage/orders")
@Tag(name = "工资核算-生产任务单")
public class WageOrderController {

    @Resource
    private WageOrderService orderService;

    @Resource
    private KingdeeWageService kingdeeWageService;

    /** 任务单分页（筛选后内存分页） */
    @GetMapping("/page")
    public Result<PageVO<WageOrderVO>> page(@RequestParam(required = false) String workshop,
                                            @RequestParam(required = false) Long groupId,
                                            @RequestParam(required = false) String settleStatus,
                                            @RequestParam(required = false) String bizStatus,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(orderService.page(workshop, groupId, settleStatus, bizStatus,
                keyword, pageNum, pageSize));
    }

    /** 用料清单：taskNo 非空=该任务单下方联动；空=用料清单查询页全部 */
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

    /** 从 MES 同步近一年生产订单（仅增量写入新单号） */
    @PostMapping("/sync")
    public Result<Map<String, Integer>> sync() {
        int added = orderService.syncFromMes();
        return Result.success("同步完成", Map.of("added", added));
    }

    /**
     * 从本地 wage_material 的 task_no → 金蝶 PRD_PPBOM → 同步到 wage_order
     * <p>用于"金蝶标准接口查询"保存用料清单后，进入生产任务单页签自动补齐任务单头信息。</p>
     */
    @PostMapping("/sync-from-materials")
    @Operation(summary = "从本地用料清单同步任务单",
            description = "取 wage_material 所有 task_no（生产订单号），剔除 wage_order 已存在的，剩余去金蝶 PRD_PPBOM 查父项产品后写入 wage_order")
    public Result<Map<String, Integer>> syncFromMaterials() {
        int added = orderService.syncFromMaterials();
        return Result.success("同步完成", Map.of("added", added));
    }

    /** 开工提交：必选组别 + 金蝶下发用料清单 */
    @PostMapping("/{id}/start")
    public Result<Boolean> start(@PathVariable Long id, @Valid @RequestBody WageOrderStartRequest request) {
        orderService.start(id, request);
        return Result.success("开工提交成功", Boolean.TRUE);
    }

    /** 确认完工 */
    @PostMapping("/{id}/done")
    public Result<Boolean> done(@PathVariable Long id, @RequestBody WageOrderDoneRequest request) {
        orderService.done(id, request);
        return Result.success("已确认完工", Boolean.TRUE);
    }

    /** 修改加工组别 */
    @PutMapping("/{id}/group")
    public Result<Boolean> changeGroup(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        orderService.changeGroup(id, body.get("groupId"));
        return Result.success("修改成功", Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        orderService.remove(id);
        return Result.success("删除成功", Boolean.TRUE);
    }

    /**
     * 生产用料清单页签：查询金蝶 PRD_PPBOM 子项物料名称包含关键词 + 创建日期范围
     *
     * @param keyword      关键词（多个用逗号分隔，如 "丝印,移印"）；空时不加关键词条件
     * @param startDate    创建日期起始，格式 yyyy-MM-dd；空则默认近3个月
     * @param limit        最大返回条数；空或 <=0 取 2000
     */
    @GetMapping("/ppbom/query")
    @Operation(summary = "查询金蝶《生产用料清单》PRD_PPBOM",
            description = "子项物料名称包含关键词（逗号/分号分隔，OR 组合）+ 创建日期范围")
    public Result<List<WagePpbomRowVO>> queryPpbom(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String startDate,
                                                    @RequestParam(required = false) Integer limit) {
        List<String> keywords = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            keywords.addAll(Arrays.asList(keyword.split("[,，;；]")));
            keywords.removeIf(String::isEmpty);
        }
        return Result.success(kingdeeWageService.queryPpbomByKeyword(keywords, startDate, limit));
    }

    /**
     * 将金蝶查询结果保存到本地 wage_material（按 task_no + material_code 幂等写入）
     */
    @PostMapping("/ppbom/save")
    @Operation(summary = "保存金蝶用料清单到本地",
            description = "接收金蝶 PRD_PPBOM 查询结果数组，映射后写入 wage_material；已存在的跳过；bizStatus 取自本地 wage_order（未命中默认计划）")
    public Result<Map<String, Integer>> savePpbom(@RequestBody List<WagePpbomRowVO> rows) {
        int added = orderService.savePpbomRows(rows);
        return Result.success(Map.of("added", added, "received", rows == null ? 0 : rows.size()));
    }
}
