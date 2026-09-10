package com.enterprise.brain.task.dto.request;

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

    @NotNull(message = "补料原因不能为空")
    @Schema(description = "补料原因ID")
    private Integer reasonId;

    @NotBlank(message = "补料原因不能为空")
    @Schema(description = "补料原因文本")
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
    }
}
