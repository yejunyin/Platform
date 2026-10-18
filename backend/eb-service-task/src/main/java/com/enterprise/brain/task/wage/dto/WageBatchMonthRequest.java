package com.enterprise.brain.task.wage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量录入工资月份（勾选明细统一改写）
 */
@Data
public class WageBatchMonthRequest {

    @NotEmpty(message = "请先勾选明细")
    private List<Long> ids;

    @NotBlank(message = "工资月份不能为空")
    private String wageMonth;

    private String remark;
}
