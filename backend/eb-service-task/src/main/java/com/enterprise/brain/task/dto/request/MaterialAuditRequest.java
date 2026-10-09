package com.enterprise.brain.task.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

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

    @Schema(description = "退料类型ID(审核通过时必传, 取自接口8列表项returnTypeId; 驳回时不传)")
    private String returnTypeId;

    @Schema(description = "退料原因ID(审核通过时必填, 审核人选择, 字典reasonType=2, 来源getReasons?reasonType=2; 驳回时不传)")
    private String reasonId;

    @Schema(description = "退料原因名称(审核通过时必填, 审核人选择; 驳回时不传)")
    private String reasonText;

    @Schema(description = "驳回原因（驳回时必填）")
    private String rejectReason;

    @Schema(description = "库存不足部分匹配时是否按最大可用量继续 0否 1是")
    private Integer forceFlag;

    @Schema(description = "物料行备注数组(选填, 审核通过auditResult=1时使用; 驳回时忽略; forceFlag=1重试时原样回传)")
    private List<MaterialMemo> materialMemos;

    /**
     * 物料行备注项：后端以 orderCode + materialCode + qty 联合键定位物料明细行
     */
    @Data
    @Schema(description = "物料行备注项")
    public static class MaterialMemo implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "生产订单编码，分录匹配键之一")
        private String orderCode;

        @Schema(description = "物料编码，分录匹配键之一")
        private String materialCode;

        @Schema(description = "本物料行退料数量，用于同一订单下相同物料多行的区分")
        private BigDecimal qty;

        @JsonProperty("FEntrtyMemo")
        @Schema(description = "物料行备注，最长200字符；后端入库前做trim与长度截断")
        private String fentrtyMemo;

        @Schema(description = "责任归属(审核人手填, 写入金蝶退料单分录Fresponsible)，最长100字符；缺省视为空串")
        private String responsible;
    }
}
