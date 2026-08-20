package com.enterprise.brain.task.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 备料明细响应 DTO (snake_case 输出，匹配前端 Material 接口)
 */
@Data
@Schema(description = "备料明细")
public class MaterialDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("task_id")
    @Schema(description = "任务单号")
    private String taskId;

    @JsonProperty("material_code")
    @Schema(description = "物料编码")
    private String materialCode;

    @JsonProperty("material_name")
    @Schema(description = "物料名称")
    private String materialName;

    @JsonProperty("specification")
    @Schema(description = "规格")
    private String specification;

    @JsonProperty("unit")
    @Schema(description = "单位")
    private String unit;

    @JsonProperty("required_qty")
    @Schema(description = "需求数量")
    private Integer requiredQty;

    @JsonProperty("available_qty")
    @Schema(description = "可用库存")
    private Integer availableQty;

    @JsonProperty("prepared_qty")
    @Schema(description = "已备数量")
    private Integer preparedQty;

    @JsonProperty("storage_location")
    @Schema(description = "库位")
    private String storageLocation;

    @JsonProperty("status")
    @Schema(description = "备料状态：pending/preparing/ready/shortage")
    private String status;

    @JsonProperty("preparer")
    @Schema(description = "备料人")
    private String preparer;

    @JsonProperty("prepare_time")
    @Schema(description = "备料时间(yyyy-MM-dd HH:mm)")
    private String prepareTime;

    @JsonProperty("remark")
    @Schema(description = "备注")
    private String remark;
}
