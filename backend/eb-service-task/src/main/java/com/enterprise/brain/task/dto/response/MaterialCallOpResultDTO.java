package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 接口5提交结果 / 接口9审核结果 / 接口10-11重试结果 共用
 */
@Data
@Schema(description = "补退料操作结果")
public class MaterialCallOpResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请单ID")
    private String id;

    @Schema(description = "申请单号")
    private String callNo;

    @Schema(description = "当前状态")
    private Integer status;

    @Schema(description = "金蝶退料单号")
    private String erpOrderNo;

    @Schema(description = "金蝶补料单号")
    private String erpReplenishOrderNo;

    @Schema(description = "WMS出库申请单号")
    private String wmsOrderNo;

    /**
     * 接口9 库存不足部分匹配数据（status=2时）
     */
    @Data
    @Schema(description = "库存不足部分匹配")
    public static class PartialMatchDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "申请总数量")
        private BigDecimal requestQty;

        @Schema(description = "最大可匹配数量")
        private BigDecimal matchQty;
    }
}
