package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口4：质检员
 */
@Data
@Schema(description = "质检员信息")
public class QcStaffDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "员工工号")
    private String staffCode;

    @Schema(description = "员工姓名")
    private String staffName;
}
