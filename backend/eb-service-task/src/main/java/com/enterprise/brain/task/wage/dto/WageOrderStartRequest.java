package com.enterprise.brain.task.wage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开工提交：必选加工组别（工资分摊前提）
 */
@Data
public class WageOrderStartRequest {

    @NotNull(message = "请选择加工组别")
    private Long groupId;

    /** 确认人工号（当前登录用户） */
    private String confirmerNo;

    /** 确认人姓名（当前登录用户） */
    private String confirmerName;
}
