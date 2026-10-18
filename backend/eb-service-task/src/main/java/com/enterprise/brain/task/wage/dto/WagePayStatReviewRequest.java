package com.enterprise.brain.task.wage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 计件明细复核（单条）
 */
@Data
public class WagePayStatReviewRequest {

    /** 复核后的工资月份（YYYY-MM），可覆盖按开工时间的归集 */
    @NotBlank(message = "工资月份不能为空")
    private String wageMonth;

    /** 个人金额（可人工覆盖；null 表示不改） */
    private BigDecimal amount;

    private String remark;

    /** 复核人（当前登录账户名） */
    @NotBlank(message = "未获取到当前登录用户，无法复核")
    private String reviewer;
}
