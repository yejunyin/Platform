package com.enterprise.brain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 备齐请求DTO
 */
@Data
@Schema(description = "备齐请求")
public class MaterialPrepareRequest {

    @NotNull(message = "本次备料数量不能为空")
    @Min(value = 1, message = "备料数量必须大于0")
    @Schema(description = "本次备料数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    private Integer qty;

    @NotBlank(message = "备料人不能为空")
    @Schema(description = "备料人", requiredMode = Schema.RequiredMode.REQUIRED, example = "张备料")
    private String preparer;
}
