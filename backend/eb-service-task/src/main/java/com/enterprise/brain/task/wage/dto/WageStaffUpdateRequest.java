package com.enterprise.brain.task.wage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 编辑员工工资资料（更新 sys_user.real_name + 工资扩展资料）
 */
@Data
public class WageStaffUpdateRequest {

    @NotBlank(message = "请填写姓名")
    private String realName;

    private String jobType;

    private Long groupId;

    private BigDecimal coef;

    private LocalDate hireDate;
}
