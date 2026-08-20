package com.enterprise.brain.task.dto.request;

import com.enterprise.brain.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务分页查询请求DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务分页查询请求")
public class TaskQueryDTO extends PageQuery {

    @Schema(description = "处理人ID（当前用户）", example = "1")
    private Long assigneeId;

    @Schema(description = "任务状态: 0-待处理, 1-处理中, 2-已完成, 3-已驳回, 4-已撤销, 5-已超时, 6-已转办")
    private Integer taskStatus;

    @Schema(description = "来源系统: OA/ERP/MES/EB")
    private String externalSystem;

    @Schema(description = "任务类型: APPROVAL-审批, NOTICE-通知, TODO-待办, REVIEW-审核")
    private String taskType;

    @Schema(description = "任务分类")
    private String taskCategory;

    @Schema(description = "优先级: 1-紧急, 2-高, 3-中, 4-低")
    private Integer priority;

    @Schema(description = "关键字搜索（标题/内容/任务编号）")
    private String keyword;

    @Schema(description = "是否已读: 0-未读, 1-已读")
    private Integer isRead;

    @Schema(description = "处理人部门ID")
    private Long assigneeDeptId;

    @Schema(description = "发起人ID")
    private Long initiatorId;
}
