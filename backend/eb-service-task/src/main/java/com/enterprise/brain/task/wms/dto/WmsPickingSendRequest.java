package com.enterprise.brain.task.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * WMS 发送请求（前端提交的业务数据；Service 会拼完整 WMS JSON）
 */
@Data
@Schema(description = "WMS 生产领料申请发送请求")
public class WmsPickingSendRequest {

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

    @NotEmpty(message = "领料明细不能为空")
    @Schema(description = "领料明细行", required = true)
    private List<Entry> entries;

    @Data
    @Schema(description = "领料明细行")
    public static class Entry {
        @NotNull(message = "子项物料编码不能为空")
        private String materialCode;
        private String materialName;
        @Schema(description = "产品编码（用于 WMS ProductCode，同一订单相同）")
        private String productCode;
        @Schema(description = "产品名称")
        private String productName;
        @NotNull(message = "数量不能为空")
        private BigDecimal quantity;
        private String unitCode;
        private String planCode = "";
        private String productionStationCode;
        private String outWarehouseCode;
    }
}
