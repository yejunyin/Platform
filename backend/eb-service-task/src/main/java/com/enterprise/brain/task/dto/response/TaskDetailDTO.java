package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务详情响应DTO
 */
@Data
@Schema(description = "任务详情")
public class TaskDetailDTO {

    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "任务编号")
    private String taskNo;

    @Schema(description = "外部系统任务ID")
    private String externalTaskId;

    @Schema(description = "来源系统编码")
    private String externalSystem;

    @Schema(description = "来源系统名称")
    private String externalSystemName;

    @Schema(description = "任务类型")
    private String taskType;

    @Schema(description = "任务类型描述")
    private String taskTypeDesc;

    @Schema(description = "任务分类")
    private String taskCategory;

    @Schema(description = "任务分类描述")
    private String taskCategoryDesc;

    @Schema(description = "优先级: 1-紧急, 2-高, 3-中, 4-低")
    private Integer priority;

    @Schema(description = "优先级描述")
    private String priorityDesc;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "任务内容/描述")
    private String content;

    @Schema(description = "业务单据号")
    private String bizKey;

    @Schema(description = "业务跳转URL")
    private String bizUrl;

    @Schema(description = "发起人ID")
    private Long initiatorId;

    @Schema(description = "发起人姓名")
    private String initiatorName;

    @Schema(description = "处理人ID")
    private Long assigneeId;

    @Schema(description = "处理人姓名")
    private String assigneeName;

    @Schema(description = "处理人部门ID")
    private Long assigneeDeptId;

    @Schema(description = "处理人部门名称")
    private String assigneeDeptName;

    @Schema(description = "任务状态")
    private Integer taskStatus;

    @Schema(description = "任务状态描述")
    private String taskStatusDesc;

    @Schema(description = "接收时间")
    private LocalDateTime receiveTime;

    @Schema(description = "截止时间")
    private LocalDateTime deadlineTime;

    @Schema(description = "开始处理时间")
    private LocalDateTime startProcessTime;

    @Schema(description = "完成时间")
    private LocalDateTime completeTime;

    @Schema(description = "处理耗时(秒)")
    private Long handleDuration;

    @Schema(description = "处理耗时显示")
    private String handleDurationDisplay;

    @Schema(description = "是否已读: 0-未读, 1-已读")
    private Integer isRead;

    @Schema(description = "是否已催办")
    private Integer isUrged;

    @Schema(description = "催办次数")
    private Integer urgeCount;

    @Schema(description = "处理结果")
    private String actionResult;

    @Schema(description = "处理结果描述")
    private String actionResultDesc;

    @Schema(description = "处理意见")
    private String actionComment;

    @Schema(description = "是否即将超时")
    private Boolean isNearDeadline;

    @Schema(description = "是否已超时")
    private Boolean isOverdue;

    @Schema(description = "处理历史列表")
    private List<TaskHistoryDTO> historyList;
}
