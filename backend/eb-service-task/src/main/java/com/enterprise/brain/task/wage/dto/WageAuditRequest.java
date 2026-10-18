package com.enterprise.brain.task.wage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工资审核：单条按 月份+工号 定位；批量时 month/keyword 可空表示当前筛选范围
 */
@Data
public class WageAuditRequest {

    /** 单条审核时的月份（批量可为空） */
    private String month;

    /** 单条审核时的工号（批量可为空） */
    private String staffNo;

    /** 批量审核时的姓名/工号/小组模糊条件 */
    private String keyword;

    /** 批量审核时的审核状态筛选 */
    private String auditStatus;

    /** true=审核通过 false=驳回（仅单条有效） */
    private Boolean approved = Boolean.TRUE;

    @NotBlank(message = "未获取到当前登录用户，无法审核")
    private String auditor;
}
