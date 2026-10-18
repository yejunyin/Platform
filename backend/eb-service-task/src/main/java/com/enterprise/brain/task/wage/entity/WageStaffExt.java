package com.enterprise.brain.task.wage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工工资扩展资料（wage_staff_ext，1:1 关联 sys_user.id）
 * <p>身份信息（工号/姓名/在职状态）取 sys_user，工资专属属性放本表。</p>
 */
@Data
@TableName("wage_staff_ext")
@Schema(description = "工资核算-员工扩展资料")
public class WageStaffExt implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "sys_user.id")
    private Long userId;

    @Schema(description = "工种：员工/组长/班长")
    private String jobType;

    @Schema(description = "所属小组 wage_group.id")
    private Long groupId;

    @Schema(description = "人员系数")
    private BigDecimal coef;

    @Schema(description = "入职日期")
    private LocalDate hireDate;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
