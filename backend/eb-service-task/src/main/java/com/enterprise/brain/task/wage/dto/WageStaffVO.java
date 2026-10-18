package com.enterprise.brain.task.wage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工工资资料（sys_user 左联 wage_staff_ext / wage_group）
 */
@Data
@Schema(description = "工资核算-员工资料")
public class WageStaffVO {

    @Schema(description = "sys_user.id")
    private Long userId;

    @Schema(description = "工号")
    private String username;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "在职状态 1在职 0离职（sys_user.status）")
    private Integer status;

    @Schema(description = "扩展记录id（null=尚未维护工资属性）")
    private Long extId;

    @Schema(description = "工种")
    private String jobType;

    @Schema(description = "所属小组id")
    private Long groupId;

    @Schema(description = "所属小组名称")
    private String groupName;

    @Schema(description = "人员系数")
    private BigDecimal coef;

    @Schema(description = "入职日期")
    private LocalDate hireDate;
}
