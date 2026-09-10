package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 补退料申请单视图（接口6列表 / 接口7详情 / 接口8待审共用）
 */
@Data
@Schema(description = "补退料申请单")
public class MaterialApplicationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请单ID")
    private String id;

    @Schema(description = "申请单号")
    private String callNo;

    @Schema(description = "状态：10待质检审核 11已驳回 20批次匹配中 30退料单生成中 31退料单生成异常 40退料单已生成 41WMS申请异常 50WMS申请已生成 99已完成")
    private Integer status;

    @Schema(description = "申请时间 yyyy-MM-dd HH:mm:ss")
    private String createTime;

    @Schema(description = "申请人姓名")
    private String applicantName;

    @Schema(description = "申请人部门")
    private String applicantDept;

    @Schema(description = "质检员姓名")
    private String qcStaffName;

    @Schema(description = "补料原因")
    private String reasonText;

    @Schema(description = "关联生产订单号(逗号分隔)")
    private String orderCodes;

    @Schema(description = "物料明细行数")
    private Integer itemCount;

    @Schema(description = "金蝶退料单号")
    private String erpOrderNo;

    @Schema(description = "金蝶补料单号")
    private String erpReplenishOrderNo;

    @Schema(description = "WMS出库申请单号")
    private String wmsOrderNo;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "退料类型 1良品 2不良品 3报废")
    private Integer returnType;

    @Schema(description = "异常描述(状态31/41)")
    private String errorMsg;

    @Schema(description = "审核时间 yyyy-MM-dd HH:mm:ss")
    private String auditTime;

    @Schema(description = "审核人")
    private String auditBy;

    @Schema(description = "是否涉及WMS仓库")
    private Boolean wmsEnabled;

    @Schema(description = "物料明细（详情接口返回）")
    private List<ItemDTO> items;

    @Schema(description = "批次匹配结果（详情接口、状态>=40时返回）")
    private List<BatchDTO> batches;

    /**
     * 申请物料明细行
     */
    @Data
    @Schema(description = "申请物料明细")
    public static class ItemDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "生产订单号")
        private String orderCode;

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

    /**
     * 批次匹配行（FIFO）
     */
    @Data
    @Schema(description = "批次匹配结果")
    public static class BatchDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "物料编码")
        private String materialCode;

        @Schema(description = "批次号")
        private String batchNo;

        @Schema(description = "库位")
        private String locationCode;

        @Schema(description = "仓库")
        private String warehouse;

        @Schema(description = "数量")
        private java.math.BigDecimal qty;

        @Schema(description = "该批次所在仓库是否启用WMS")
        private Boolean wmsFlag;
    }
}
