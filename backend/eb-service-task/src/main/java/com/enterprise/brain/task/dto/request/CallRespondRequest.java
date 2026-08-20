package com.enterprise.brain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 叫料响应请求DTO
 */
@Data
@Schema(description = "叫料响应请求")
public class CallRespondRequest {

    @NotBlank(message = "响应人不能为空")
    @Schema(description = "响应人", requiredMode = Schema.RequiredMode.REQUIRED, example = "仓管赵")
    private String responder;
}
