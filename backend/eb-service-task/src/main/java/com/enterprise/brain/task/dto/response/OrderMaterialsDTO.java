package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 接口2：订单用料清单（按订单分组）
 */
@Data
@Schema(description = "订单用料清单")
public class OrderMaterialsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "生产订单号")
    private String orderCode;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "物料明细")
    private List<MaterialLineDTO> materials;

    /**
     * 用料行：应发/已领/差异/可用库存
     */
    @Data
    @Schema(description = "用料清单行")
    public static class MaterialLineDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "物料编码")
        private String materialCode;

        @Schema(description = "物料名称")
        private String materialName;

        @Schema(description = "规格型号")
        private String spec;

        @Schema(description = "单位")
        private String unit;

        @Schema(description = "应发数量")
        private BigDecimal shouldQty;

        @Schema(description = "已领数量")
        private BigDecimal issuedQty;

        @Schema(description = "差异数量 = 应发-已领")
        private BigDecimal diffQty;

        @Schema(description = "当前可用库存(即时库存)")
        private BigDecimal stockQty;
    }
}
