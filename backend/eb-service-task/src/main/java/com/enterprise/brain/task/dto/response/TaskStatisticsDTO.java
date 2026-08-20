package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务统计响应DTO
 */
@Data
@Schema(description = "任务统计信息")
public class TaskStatisticsDTO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "待处理任务数")
    private Long pendingCount;

    @Schema(description = "处理中任务数")
    private Long processingCount;

    @Schema(description = "已完成任务数")
    private Long completedCount;

    @Schema(description = "超时任务数")
    private Long overdueCount;

    @Schema(description = "紧急任务数")
    private Long urgentCount;

    @Schema(description = "未读任务数")
    private Long unreadCount;

    @Schema(description = "今日新增任务数")
    private Long todayNewCount;

    @Schema(description = "今日完成任务数")
    private Long todayCompletedCount;

    @Schema(description = "平均处理时长(秒)")
    private Double avgHandleDuration;

    @Schema(description = "按期完成率(%)")
    private Double onTimeRate;
}
