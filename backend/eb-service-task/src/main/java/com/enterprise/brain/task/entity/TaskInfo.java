package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.enterprise.brain.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 统一任务主表实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_info")
@Schema(description = "统一任务信息")
public class TaskInfo extends BaseEntity {

    @Schema(description = "任务编号")
    private String taskNo;

    @Schema(description = "外部系统任务ID")
    private String externalTaskId;

    @Schema(description = "来源系统编码: OA/ERP/MES/EB")
    private String externalSystem;

    @Schema(description = "任务类型: APPROVAL-审批, NOTICE-通知, TODO-待办, REVIEW-审核")
    private String taskType;

    @Schema(description = "任务分类")
    private String taskCategory;

    @Schema(description = "优先级: 1-紧急, 2-高, 3-中, 4-低")
    private Integer priority;

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

    @Schema(description = "抄送人ID列表")
    private String ccUserIds;

    @Schema(description = "任务状态: 0-待处理, 1-处理中, 2-已完成, 3-已驳回, 4-已撤销, 5-已超时, 6-已转办")
    private Integer taskStatus;

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

    @Schema(description = "是否已读: 0-未读, 1-已读")
    private Integer isRead;

    @Schema(description = "是否已催办: 0-否, 1-是")
    private Integer isUrged;

    @Schema(description = "催办次数")
    private Integer urgeCount;

    @Schema(description = "处理结果")
    private String actionResult;

    @Schema(description = "处理意见")
    private String actionComment;

    @Schema(description = "排序权重")
    private Integer sortWeight;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    @Schema(description = "扩展数据JSON")
    private String extData;
}
