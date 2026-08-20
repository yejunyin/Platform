package com.enterprise.brain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 叫料请求DTO
 */
@Data
@Schema(description = "叫料请求")
public class MaterialCallCreateRequest {

    @NotBlank(message = "任务单号不能为空")
    @Schema(description = "任务单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "WO-20260815-001")
    private String taskId;

    @NotBlank(message = "物料编码不能为空")
    @Schema(description = "物料编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "M-IC-002")
    private String materialCode;

    @NotBlank(message = "物料名称不能为空")
    @Schema(description = "物料名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "主控芯片STM32")
    private String materialName;

    @NotNull(message = "需求数量不能为空")
    @Min(value = 0, message = "需求数量不能为负")
    @Schema(description = "需求数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "500")
    private Integer requiredQty;

    @NotNull(message = "叫料数量不能为空")
    @Min(value = 1, message = "叫料数量必须大于0")
    @Schema(description = "叫料数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    private Integer callQty;

    @NotBlank(message = "叫料类型不能为空")
    @Schema(description = "叫料类型：normal/urgent", requiredMode = Schema.RequiredMode.REQUIRED, example = "urgent")
    private String callType;

    @NotBlank(message = "叫料人不能为空")
    @Schema(description = "叫料人", requiredMode = Schema.RequiredMode.REQUIRED, example = "张备料")
    private String caller;

    @Schema(description = "备注", example = "库存不足")
    private String remark;
}
