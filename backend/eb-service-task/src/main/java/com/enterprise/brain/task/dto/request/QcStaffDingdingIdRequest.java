package com.enterprise.brain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 维护质检人员钉钉ID请求（传空可清空绑定）
 */
@Data
@Schema(description = "维护质检人员钉钉ID请求")
public class QcStaffDingdingIdRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 64, message = "钉钉ID长度不能超过64")
    @Schema(description = "钉钉ID（sys_user.dingdingid，为空表示清空）", example = "zhangsan01")
    private String dingdingId;
}
