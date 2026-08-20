package com.enterprise.brain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建任务请求DTO
 */
@Data
@Schema(description = "创建任务请求")
public class TaskCreateDTO {

    @Schema(description = "外部系统任务ID")
    private String externalTaskId;

    @NotBlank(message = "来源系统不能为空")
    @Schema(description = "来源系统编码: OA/ERP/MES/EB", requiredMode = Schema.RequiredMode.REQUIRED, example = "EB")
    private String externalSystem;

    @NotBlank(message = "任务类型不能为空")
    @Schema(description = "任务类型: APPROVAL-审批, NOTICE-通知, TODO-待办, REVIEW-审核", requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVAL")
    private String taskType;

    @Schema(description = "任务分类", example = "LEAVE")
    private String taskCategory;

    @Schema(description = "优先级: 1-紧急, 2-高, 3-中, 4-低", example = "2")
    private Integer priority = 2;

    @NotBlank(message = "任务标题不能为空")
    @Schema(description = "任务标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "【审批】年假申请")
    private String title;

    @Schema(description = "任务内容/描述", example = "申请年假5天")
    private String content;

    @Schema(description = "业务单据号", example = "LV202608001")
    private String bizKey;

    @Schema(description = "业务跳转URL", example = "http://xxx.com/leave/123")
    private String bizUrl;

    @Schema(description = "发起人ID", example = "2")
    private Long initiatorId;

    @Schema(description = "发起人姓名", example = "张三")
    private String initiatorName;

    @NotNull(message = "处理人ID不能为空")
    @Schema(description = "处理人ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long assigneeId;

    @NotBlank(message = "处理人姓名不能为空")
    @Schema(description = "处理人姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "系统管理员")
    private String assigneeName;

    @Schema(description = "处理人部门ID", example = "1")
    private Long assigneeDeptId;

    @Schema(description = "处理人部门名称", example = "集团总部")
    private String assigneeDeptName;

    @Schema(description = "抄送人ID列表(逗号分隔)", example = "2,3")
    private String ccUserIds;

    @Schema(description = "截止时间", example = "2026-08-20T18:00:00")
    private LocalDateTime deadlineTime;

    @Schema(description = "排序权重", example = "0")
    private Integer sortWeight = 0;

    @Schema(description = "扩展数据JSON")
    private String extData;
}
