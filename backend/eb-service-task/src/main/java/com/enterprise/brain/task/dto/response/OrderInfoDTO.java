package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 接口1：生产订单信息
 */
@Data
@Schema(description = "生产订单信息")
public class OrderInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "生产订单号", example = "WORK26091429592A")
    private String orderCode;

    @Schema(description = "ERP订单号(金蝶生产订单号)")
    private String erpOrderCode;

    @Schema(description = "产品物料编码")
    private String productCode;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "计划数量")
    private BigDecimal planQty;

    @Schema(description = "已完工汇报数量")
    private BigDecimal producedQty;

    @Schema(description = "订单状态：1已下达 2生产中 3已完工")
    private Integer orderStatus;

    @Schema(description = "订单状态文本")
    private String orderStatusText;

    @Schema(description = "计划完工时间 yyyy-MM-dd")
    private String planEndTime;
}
