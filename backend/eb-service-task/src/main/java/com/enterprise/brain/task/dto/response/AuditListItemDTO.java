package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 接口8：质检待审列表项（含完整补料上下文）
 */
@Data
@Schema(description = "质检待审申请")
public class AuditListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请单ID")
    private String id;

    @Schema(description = "申请单号")
    private String callNo;

    @Schema(description = "申请时间 yyyy-MM-dd HH:mm:ss")
    private String createTime;

    @Schema(description = "申请人姓名")
    private String applicantName;

    @Schema(description = "申请人部门")
    private String applicantDept;

    @Schema(description = "补料原因")
    private String reasonText;

    @Schema(description = "订单及物料明细")
    private List<OrderDTO> orderList;

    /**
     * 订单分组（待审上下文）
     */
    @Data
    @Schema(description = "订单分组")
    public static class OrderDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "生产订单号")
        private String orderCode;

        @Schema(description = "产品名称")
        private String productName;

        @Schema(description = "物料明细")
        private List<MaterialDTO> materials;
    }

    /**
     * 待审物料行
     */
    @Data
    @Schema(description = "待审物料行")
    public static class MaterialDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "物料编码")
        private String materialCode;

        @Schema(description = "物料名称")
        private String materialName;

        @Schema(description = "规格型号")
        private String spec;

        @Schema(description = "单位")
        private String unit;

        @Schema(description = "申请数量")
        private java.math.BigDecimal qty;
    }
}
