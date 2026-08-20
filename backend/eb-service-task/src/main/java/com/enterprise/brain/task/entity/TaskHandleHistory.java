package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务处理历史实体
 */
@Data
@TableName("task_handle_history")
@Schema(description = "任务处理历史")
public class TaskHandleHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
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

    @Schema(description = "操作类型: CREATE/CLAIM/APPROVE/REJECT/TRANSFER/DELEGATE/URGE/COMMENT/REVOKE")
    private String actionType;

    @Schema(description = "处理结果")
    private String actionResult;

    @Schema(description = "处理意见/备注")
    private String actionComment;

    @Schema(description = "上一处理人ID")
    private Long previousHandlerId;

    @Schema(description = "下一处理人ID")
    private Long nextHandlerId;

    @Schema(description = "附件URL")
    private String attachments;

    @Schema(description = "节点处理耗时(秒)")
    private Long handleDuration;

    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
