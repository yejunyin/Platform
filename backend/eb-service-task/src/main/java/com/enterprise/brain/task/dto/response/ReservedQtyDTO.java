package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 进行中申请的库存预占汇总（物料+批次）
 */
@Data
@Schema(description = "库存预占汇总")
public class ReservedQtyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "物料编码")
    private String materialCode;

    @Schema(description = "批次号")
    private String batchNo;

    @Schema(description = "预占数量")
    private BigDecimal qty;
}
