package com.enterprise.brain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口9：质检审核请求体
 */
@Data
@Schema(description = "补退料质检审核请求")
public class MaterialAuditRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "申请单ID不能为空")
    @Schema(description = "申请单ID")
    private String id;

    @Schema(description = "审核员工号")
    private String auditStaffId;

    @Schema(description = "审核员姓名")
    private String auditStaffName;

    @NotNull(message = "审核结果不能为空")
    @Schema(description = "审核结果 1通过 2驳回")
    private Integer auditResult;

    @Schema(description = "退料类型（已废弃，后端忽略；退料类型改由申请单补料原因决定）")
    private Integer returnTypeId;

    @Schema(description = "驳回原因（驳回时必填）")
    private String rejectReason;

    @Schema(description = "库存不足部分匹配时是否按最大可用量继续 0否 1是")
    private Integer forceFlag;
}
