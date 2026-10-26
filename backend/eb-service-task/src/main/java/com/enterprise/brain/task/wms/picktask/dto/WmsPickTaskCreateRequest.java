package com.enterprise.brain.task.wms.picktask.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建定时 WMS 领料任务请求
 */
@Data
@Schema(description = "创建定时 WMS 领料任务请求")
public class WmsPickTaskCreateRequest {

    @NotBlank(message = "生产订单号不能为空")
    @Schema(description = "生产订单号（WORKxxxxxx）", required = true)
    private String productionNo;

    @NotBlank(message = "生产线编码不能为空")
    @Schema(description = "生产线编码（LB-CX-07 等，用户手工输入）", required = true)
    private String lineCode;

    @Schema(description = "组织编码，默认 102")
    private String orgCode = "102";

    @Schema(description = "部门编码，默认 1022016")
    private String deptCode = "1022016";

    @NotNull(message = "间隔时间不能为空")
    @Positive(message = "间隔时间必须大于 0")
    @Schema(description = "间隔时间（分钟）", required = true)
    private Integer intervalSec = 1;

    @NotEmpty(message = "任务明细不能为空")
    @Schema(description = "任务明细行（每行一个物料）", required = true)
    private List<Entry> entries;

    @Data
    @Schema(description = "任务明细行")
    public static class Entry {
        @NotNull(message = "物料编码不能为空")
        private String materialCode;

        private String materialName;

        @Schema(description = "产品编码（用于 WMS ProductCode）")
        private String productCode;

        @Schema(description = "产品名称")
        private String productName;

        @NotNull(message = "发料总数量不能为空")
        @Positive(message = "发料总数量必须大于 0")
        private BigDecimal totalQty;

        @NotNull(message = "每箱数量不能为空")
        @Positive(message = "每箱数量必须大于 0")
        private BigDecimal perBoxQty;

        private String unitCode;

        private String planCode = "";

        private String productionStationCode;

        private String outWarehouseCode;

        @Schema(description = "金蝶应发数量（参考用，前端查用料清单时带下来）")
        private BigDecimal planQty;
    }
}
