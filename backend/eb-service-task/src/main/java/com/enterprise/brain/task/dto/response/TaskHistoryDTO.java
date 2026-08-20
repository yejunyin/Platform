package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务处理历史响应DTO
 */
@Data
@Schema(description = "任务处理历史记录")
public class TaskHistoryDTO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "流程节点编码")
    private String nodeCode;

    @Schema(description = "流程节点名称")
    private String nodeName;

    @Schema(description = "处理人ID")
    private Long handlerId;

    @Schema(description = "处理人姓名")
    private String handlerName;

    @Schema(description = "处理人部门ID")
    private Long handlerDeptId;

    @Schema(description = "处理人部门名称")
    private String handlerDeptName;

    @Schema(description = "操作类型")
    private String actionType;

    @Schema(description = "操作类型描述")
    private String actionTypeDesc;

    @Schema(description = "处理结果")
    private String actionResult;

    @Schema(description = "处理结果描述")
    private String actionResultDesc;

    @Schema(description = "处理意见/备注")
    private String actionComment;

    @Schema(description = "附件URL")
    private String attachments;

    @Schema(description = "节点处理耗时(秒)")
    private Long handleDuration;

    @Schema(description = "处理耗时显示")
    private String handleDurationDisplay;

    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
