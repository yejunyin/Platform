package com.enterprise.brain.task.wage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增员工（创建 sys_user + 工资扩展资料）
 */
@Data
public class WageStaffSaveRequest {

    @NotBlank(message = "请填写工号")
    private String username;

    @NotBlank(message = "请填写姓名")
    private String realName;

    /** 工种：员工/组长/班长 */
    private String jobType;

    /** 所属小组id（可空） */
    private Long groupId;

    /** 人员系数，空则默认 1 */
    private BigDecimal coef;

    private LocalDate hireDate;
}
