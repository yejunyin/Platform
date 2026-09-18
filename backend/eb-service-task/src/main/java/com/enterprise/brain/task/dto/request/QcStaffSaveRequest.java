package com.enterprise.brain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增质检人员请求
 */
@Data
@Schema(description = "新增质检人员请求")
public class QcStaffSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "工号不能为空")
    @Size(max = 64, message = "工号长度不能超过64")
    @Schema(description = "工号（sys_user.username，唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "10086")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64")
    @Schema(description = "姓名（sys_user.real_name）", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String realName;

    @Size(max = 64, message = "钉钉ID长度不能超过64")
    @Schema(description = "钉钉ID（sys_user.dingdingid，选填）", example = "zhangsan01")
    private String dingdingId;
}
