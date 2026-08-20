package com.enterprise.brain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务处理请求DTO
 */
@Data
@Schema(description = "任务处理请求")
public class TaskHandleDTO {

    @NotNull(message = "任务ID不能为空")
    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long taskId;

    @NotNull(message = "处理人ID不能为空")
    @Schema(description = "处理人ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long handlerId;

    @NotBlank(message = "处理人姓名不能为空")
    @Schema(description = "处理人姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "系统管理员")
    private String handlerName;

    @Schema(description = "处理人部门ID", example = "1")
    private Long handlerDeptId;

    @Schema(description = "处理人部门名称", example = "集团总部")
    private String handlerDeptName;

    @NotBlank(message = "操作类型不能为空")
    @Schema(description = "操作类型: CLAIM-领取, APPROVE-同意, REJECT-驳回, TRANSFER-转办, URGE-催办, COMMENT-评论",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVE")
    private String actionType;

    @Schema(description = "处理结果: AGREE-同意, REJECT-驳回等", example = "AGREE")
    private String actionResult;

    @Schema(description = "处理意见/备注", example = "同意申请")
    private String actionComment;

    @Schema(description = "转办目标人ID（转办时必填）", example = "3")
    private Long transferToUserId;

    @Schema(description = "转办目标人姓名（转办时必填）", example = "李四")
    private String transferToUserName;

    @Schema(description = "转办原因（转办时填写）", example = "出差，转由李四处理")
    private String transferReason;
}
