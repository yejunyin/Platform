package com.enterprise.brain.task.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 接口5：提交补退料申请请求体
 */
@Data
@Schema(description = "补退料申请提交请求")
public class MaterialSubmitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请人数据库ID")
    private String applicantStaffId;

    @NotBlank(message = "申请人姓名不能为空")
    @Schema(description = "申请人姓名")
    private String applicantName;

    @Schema(description = "申请人部门")
    private String applicantDept;

    @JsonProperty("Forg")
    @Schema(description = "归属组织(选填): 金蝶统计用文本，原样落库透传，不传为null")
    private String forg;

    @JsonProperty("FGroup")
    @Schema(description = "组别(选填): 金蝶统计用文本，原样落库透传，不传为null")
    private String fGroup;

    @NotNull(message = "退料类型不能为空")
    @Schema(description = "退料类型字典ID(接口3 reasonType=1 返回项id, 金蝶退料单必需)")
    private String returnTypeId;

    @NotBlank(message = "退料类型不能为空")
    @Schema(description = "退料类型文案, 冗余存储供展示")
    private String returnTypeName;

    /** 2026-09-22变更：退料原因改由质检审核人在审核通过时填写，提交时不再传值，后端忽略/置空 */
    @Schema(description = "已废弃：退料原因改由审核人审核通过时填写(audit接口)，提交时即使传值也忽略")
    private String reasonId;

    @Schema(description = "已废弃：退料原因改由审核人审核通过时填写(audit接口)，提交时即使传值也忽略")
    private String reasonText;

    @NotBlank(message = "质检员不能为空")
    @Schema(description = "质检员ID")
    private String qcStaffId;

    @Schema(description = "质检员工号")
    private String qcStaffCode;

    @NotBlank(message = "质检员姓名不能为空")
    @Schema(description = "质检员姓名")
    private String qcStaffName;

    @NotEmpty(message = "订单及物料明细不能为空")
    @Schema(description = "订单列表")
    private List<OrderGroup> orderList;

    /**
     * 订单分组
     */
    @Data
    @Schema(description = "订单分组")
    public static class OrderGroup implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotBlank(message = "订单号不能为空")
        @Schema(description = "生产订单号")
        private String orderCode;

        @NotEmpty(message = "物料明细不能为空")
        @Schema(description = "物料明细")
        private List<MaterialLine> materials;
    }

    /**
     * 物料行
     */
    @Data
    @Schema(description = "补料物料行")
    public static class MaterialLine implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotBlank(message = "物料编码不能为空")
        @Schema(description = "物料编码")
        private String materialCode;

        @Schema(description = "物料名称")
        private String materialName;

        @Schema(description = "规格型号")
        private String spec;

        @Schema(description = "单位")
        private String unit;

        @NotNull(message = "补料数量不能为空")
        @Positive(message = "补料数量必须大于0")
        @Schema(description = "补料数量")
        private BigDecimal qty;

        @JsonProperty("FEntrtyMemo")
        @Schema(description = "申请人填写的物料行备注(选填)，最长200字符；后端入库前trim、超长截断")
        private String fentrtyMemo;
    }
}
