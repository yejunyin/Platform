package com.enterprise.brain.task.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 物料预警响应 DTO (snake_case 输出，匹配前端 Alert 接口)
 */
@Data
@Schema(description = "物料预警")
public class MaterialAlertDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("task_id")
    @Schema(description = "任务单号")
    private String taskId;

    @JsonProperty("type")
    @Schema(description = "预警类型：shortage/low_stock/overtime")
    private String type;

    @JsonProperty("level")
    @Schema(description = "预警级别：danger/warning/info")
    private String level;

    @JsonProperty("message")
    @Schema(description = "预警信息")
    private String message;

    @JsonProperty("status")
    @Schema(description = "预警状态：active/resolved")
    private String status;

    @JsonProperty("created_at")
    @Schema(description = "创建时间(yyyy-MM-dd HH:mm)")
    private String createdAt;
}
