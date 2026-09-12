package com.enterprise.brain.task.controller;

import com.enterprise.brain.task.dto.request.MaterialAuditRequest;
import com.enterprise.brain.task.dto.request.MaterialSubmitRequest;
import com.enterprise.brain.task.dto.response.AuditListItemDTO;
import com.enterprise.brain.task.dto.response.MaterialApplicationDTO;
import com.enterprise.brain.task.dto.response.MaterialCallOpResultDTO;
import com.enterprise.brain.task.dto.response.MaterialResult;
import com.enterprise.brain.task.dto.response.OrderInfoDTO;
import com.enterprise.brain.task.dto.response.OrderMaterialsDTO;
import com.enterprise.brain.task.dto.response.QcStaffDTO;
import com.enterprise.brain.task.service.MaterialReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 补退料（生产补料/生产退料）接口
 * <p>对应《补退料后端接口文档》11个接口；统一响应 { "status": 0, "msg": "success", "data": {} }。</p>
 */
@Slf4j
@RestController
@RequestMapping("/DBMaterialCall")
@Tag(name = "补退料", description = "生产补料/生产退料系统接口（金蝶ERP+WMS联动）")
public class DBMaterialCallController {

    @Resource
    private MaterialReturnService materialReturnService;

    // ==================== 接口1：获取生产订单信息 ====================

    @GetMapping("/getOrder")
    @Operation(summary = "获取生产订单信息", description = "调金蝶PRD_MO查询订单；仅已下达/生产中状态允许补料")
    public MaterialResult<OrderInfoDTO> getOrder(
            @Parameter(description = "生产订单号", required = true) @RequestParam("orderCode") String orderCode,
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username) {
        return ok(() -> materialReturnService.getOrder(orderCode, username));
    }

    // ==================== 接口2：获取订单用料清单 ====================

    @GetMapping("/getOrderMaterials")
    @Operation(summary = "获取订单用料清单", description = "调金蝶PRD_PPBOM+即时库存，计算应发/已领/差异/可用库存；orderCodes逗号分隔支持多订单")
    public MaterialResult<List<OrderMaterialsDTO>> getOrderMaterials(
            @Parameter(description = "生产订单号(逗号分隔)", required = true) @RequestParam("orderCodes") String orderCodes,
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username) {
        return ok(() -> materialReturnService.getOrderMaterials(splitCodes(orderCodes), username));
    }

    // ==================== 接口3：获取原因字典（退料类型/退料原因） ====================

    @GetMapping("/getReasons")
    @Operation(summary = "获取原因字典", description = "reasonType=1退料类型(金蝶退料单必需字段, id即FReturnType枚举值: 1良品退料 2来料不良退料) / reasonType=2退料原因(业务描述)")
    public MaterialResult<List<Map<String, Object>>> getReasons(
            @Parameter(description = "字典类型 1退料类型 2退料原因") @RequestParam(value = "reasonType", required = false, defaultValue = "1") String reasonType) {
        return ok(() -> materialReturnService.getReasons(reasonType));
    }

    // ==================== 接口4：获取质检员列表 ====================

    @GetMapping("/getQcStaffList")
    @Operation(summary = "获取质检员列表", description = "从员工表筛选质检岗位人员")
    public MaterialResult<List<QcStaffDTO>> getQcStaffList(
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username) {
        return ok(() -> materialReturnService.getQcStaffList(username));
    }

    // ==================== 接口5：提交补退料申请 ====================

    @PostMapping("/submit")
    @Operation(summary = "提交补退料申请", description = "服务端二次校验数量(0<qty<=可补数量)；生成申请单(状态10待质检审核)并推送质检员")
    public MaterialResult<MaterialCallOpResultDTO> submit(
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username,
            @Valid @RequestBody MaterialSubmitRequest request) {
        return ok(() -> materialReturnService.submit(request, username));
    }

    // ==================== 接口6：获取我的申请列表 ====================

    @GetMapping("/getMyApplications")
    @Operation(summary = "获取我的申请列表", description = "status为空返回全部，多个状态逗号分隔；startTime/endTime支持yyyy-MM-dd或yyyy-MM-dd HH:mm:ss")
    public MaterialResult<List<MaterialApplicationDTO>> getMyApplications(
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username,
            @Parameter(description = "状态筛选(逗号分隔)") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "开始时间") @RequestParam(value = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(value = "endTime", required = false) String endTime) {
        return ok(() -> materialReturnService.getMyApplications(username, status, startTime, endTime));
    }

    // ==================== 接口7：获取申请单详情 ====================

    @GetMapping("/getApplicationDetail")
    @Operation(summary = "获取申请单详情", description = "返回申请主信息、物料明细及批次匹配结果(状态>=20含批次)")
    public MaterialResult<MaterialApplicationDTO> getApplicationDetail(
            @Parameter(description = "申请单ID", required = true) @RequestParam("id") String id,
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username) {
        return ok(() -> materialReturnService.getApplicationDetail(id, username));
    }

    // ==================== 接口8：获取质检待审列表 ====================

    @GetMapping("/getAuditList")
    @Operation(summary = "获取质检待审列表", description = "返回当前质检员名下状态=10的申请，含完整补料上下文")
    public MaterialResult<List<AuditListItemDTO>> getAuditList(
            @Parameter(description = "质检员工号") @RequestParam(value = "username", required = false) String username) {
        return ok(() -> materialReturnService.getAuditList(username));
    }

    // ==================== 接口9：质检审核 ====================

    @PostMapping("/audit")
    @Operation(summary = "质检审核", description = "驳回(必填原因)或通过(必传returnTypeId, 取自接口8列表项)触发FIFO匹配+金蝶退料单(携带退料类型)+WMS出库申请流水线；"
            + "库存不足且forceFlag=0时返回status=2部分匹配, forceFlag=1重发时returnTypeId原样保留")
    public MaterialResult<?> audit(
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username,
            @Valid @RequestBody MaterialAuditRequest request) {
        return raw(() -> materialReturnService.audit(request, username));
    }

    // ==================== 接口10：重试生成ERP退料单 ====================

    @PostMapping("/retryErpOrder")
    @Operation(summary = "重试生成ERP退料单", description = "仅状态=31时允许；重新调金蝶生成退料单(已保存单号自动续传，避免重复建单)")
    public MaterialResult<MaterialCallOpResultDTO> retryErpOrder(
            @Parameter(description = "申请单ID", required = true) @RequestParam("id") String id,
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username) {
        return ok(() -> materialReturnService.retryErpOrder(id, username));
    }

    // ==================== 接口11：重试生成WMS出库申请 ====================

    @PostMapping("/retryWmsOrder")
    @Operation(summary = "重试生成WMS出库申请", description = "仅状态=41时允许；无需重新生成ERP退料单，仅重发WMS申请")
    public MaterialResult<MaterialCallOpResultDTO> retryWmsOrder(
            @Parameter(description = "申请单ID", required = true) @RequestParam("id") String id,
            @Parameter(description = "操作人工号") @RequestParam(value = "username", required = false) String username) {
        return ok(() -> materialReturnService.retryWmsOrder(id, username));
    }

    // ==================== 内部工具 ====================

    /** 参数校验失败转 MaterialResult（本接口组的响应格式与全局处理器不同） */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.web.bind.MethodArgumentNotValidException.class)
    public MaterialResult<Void> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(org.springframework.validation.FieldError::getDefaultMessage)
                .findFirst().orElse("参数校验失败");
        return MaterialResult.error(msg);
    }

    /** 包装普通返回值 */
    private <T> MaterialResult<T> ok(Supplier<T> supplier) {
        try {
            return MaterialResult.ok(supplier.get());
        } catch (IllegalArgumentException e) {
            log.warn("[补退料] 业务校验失败: {}", e.getMessage());
            return MaterialResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[补退料] 接口处理异常", e);
            return MaterialResult.error("系统异常：" + e.getMessage());
        }
    }

    /** 包装已返回 MaterialResult 的调用（如质检审核的部分匹配） */
    private MaterialResult<?> raw(Supplier<MaterialResult<?>> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException e) {
            log.warn("[补退料] 业务校验失败: {}", e.getMessage());
            return MaterialResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[补退料] 接口处理异常", e);
            return MaterialResult.error("系统异常：" + e.getMessage());
        }
    }

    private List<String> splitCodes(String orderCodes) {
        if (orderCodes == null || orderCodes.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(orderCodes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}
