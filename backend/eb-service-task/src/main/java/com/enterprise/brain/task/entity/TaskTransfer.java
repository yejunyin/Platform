package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务转办/委派记录实体
 */
@Data
@TableName("task_transfer")
@Schema(description = "任务转办委派记录")
public class TaskTransfer implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "类型: 1-转办, 2-委派")
    private Integer transferType;

    @Schema(description = "原处理人ID")
    private Long fromUserId;

    @Schema(description = "原处理人姓名")
    private String fromUserName;

    @Schema(description = "目标处理人ID")
    private Long toUserId;

    @Schema(description = "目标处理人姓名")
    private String toUserName;

    @Schema(description = "转办原因")
    private String reason;

    @Schema(description = "转办时间")
    private LocalDateTime transferTime;

    @Schema(description = "委派是否归还: 0-否, 1-是")
    private Integer isReturn;

    @Schema(description = "归还时间")
    private LocalDateTime returnTime;
}
