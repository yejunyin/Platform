package com.enterprise.brain.task.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.dto.request.CallRespondRequest;
import com.enterprise.brain.task.dto.request.MaterialCallCreateRequest;
import com.enterprise.brain.task.dto.request.MaterialPrepareRequest;
import com.enterprise.brain.task.dto.response.MaterialAlertDTO;
import com.enterprise.brain.task.dto.response.MaterialCallRecordDTO;
import com.enterprise.brain.task.dto.response.MaterialCallTaskDTO;
import com.enterprise.brain.task.dto.response.MaterialDetailDTO;
import com.enterprise.brain.task.service.MaterialCallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 物料呼叫系统接口
 */
@RestController
@RequestMapping("/api/v1/material-call")
@Tag(name = "物料呼叫", description = "物料呼叫系统相关接口")
public class MaterialCallController {

    @Resource
    private MaterialCallService materialCallService;

    @GetMapping("/tasks")
    @Operation(summary = "获取生产任务列表", description = "从 MES mes_dwd_productOrder 表拉取生产任务")
    public Result<List<MaterialCallTaskDTO>> listTasks() {
        return Result.success(materialCallService.listTasks());
    }

    @GetMapping("/materials")
    @Operation(summary = "获取备料明细", description = "从 mes_dwd_material_detail 查询备料明细；可按任务号筛选")
    public Result<List<MaterialDetailDTO>> listMaterials(
            @Parameter(description = "任务单号；空或不填查全部")
            @RequestParam(value = "taskId", required = false) String taskId) {
        return Result.success(materialCallService.listMaterials(taskId));
    }

    @GetMapping("/materials/sync")
    @Operation(summary = "同步备料明细（按需从金蝶 ERP 拉取）",
            description = "判断 mes_dwd_productOrder.BOMflag：1=直接返回 mes_dwd_material_detail 数据；" +
                    "0=调用金蝶登录 → ExecuteBillQuery 拉取生产用料清单 → 写入 mes_dwd_material_detail " +
                    "并更新 BOMflag=1，再返回。")
    public Result<List<MaterialDetailDTO>> syncMaterials(
            @Parameter(description = "任务单号（必填，对应 mes_dwd_productOrder.pcode）", required = true)
            @RequestParam("taskId") String taskId) {
        return Result.success(materialCallService.syncMaterials(taskId));
    }

    @PostMapping("/materials/{id}/start-prepare")
    @Operation(summary = "备料（标记备料中）",
            description = "将指定备料明细状态置为 preparing，记录备料人/时间（备料人仅在原值为空时回填）")
    public Result<MaterialDetailDTO> startPrepare(
            @Parameter(description = "备料明细主键 id", required = true) @PathVariable Integer id) {
        return Result.success(materialCallService.startPrepare(id));
    }

    @PostMapping("/materials/{id}/prepare")
    @Operation(summary = "备齐",
            description = "累加本次备料数量到 prepared_qty；满足需求则状态置 ready，否则置 shortage 并写入缺口备注")
    public Result<MaterialDetailDTO> prepareMaterial(
            @Parameter(description = "备料明细主键 id", required = true) @PathVariable Integer id,
            @Valid @RequestBody MaterialPrepareRequest request) {
        return Result.success(materialCallService.prepareMaterial(id, request));
    }

    @GetMapping("/calls")
    @Operation(summary = "获取叫料记录", description = "从 mes_dwd_material_call 查询叫料记录；按状态筛选(all/pending/delivering/delivered)")
    public Result<List<MaterialCallRecordDTO>> listCalls(
            @Parameter(description = "叫料状态：all/pending/delivering/delivered，默认 all")
            @RequestParam(value = "status", required = false, defaultValue = "all") String status) {
        return Result.success(materialCallService.listCalls(status));
    }

    @PostMapping("/calls")
    @Operation(summary = "叫料",
            description = "写入一条 pending 叫料记录；同时将对应备料明细置为 shortage、生成缺料预警")
    public Result<MaterialCallRecordDTO> createCall(@Valid @RequestBody MaterialCallCreateRequest request) {
        return Result.success(materialCallService.createCall(request));
    }

    @PostMapping("/calls/{id}/respond")
    @Operation(summary = "响应叫料",
            description = "将叫料记录状态由 pending 置为 delivering，记录响应人/响应时间")
    public Result<MaterialCallRecordDTO> respondCall(
            @Parameter(description = "叫料记录主键 id", required = true) @PathVariable Integer id,
            @Valid @RequestBody CallRespondRequest request) {
        return Result.success(materialCallService.respondCall(id, request.getResponder()));
    }

    @PostMapping("/calls/{id}/deliver")
    @Operation(summary = "确认送达",
            description = "将叫料记录状态由 delivering 置为 delivered，记录送达时间；同时累加对应备料明细的已备数量、" +
                    "按是否满足需求更新其状态（ready / shortage），并将对应缺料预警置为 resolved")
    public Result<MaterialCallRecordDTO> deliverCall(
            @Parameter(description = "叫料记录主键 id", required = true) @PathVariable Integer id) {
        return Result.success(materialCallService.deliverCall(id));
    }

    @GetMapping("/alerts")
    @Operation(summary = "获取预警列表", description = "从 mes_dwd_material_alert 查询预警；按状态筛选(all/active/resolved)")
    public Result<List<MaterialAlertDTO>> listAlerts(
            @Parameter(description = "预警状态：all/active/resolved，默认 all")
            @RequestParam(value = "status", required = false, defaultValue = "all") String status) {
        return Result.success(materialCallService.listAlerts(status));
    }
}
