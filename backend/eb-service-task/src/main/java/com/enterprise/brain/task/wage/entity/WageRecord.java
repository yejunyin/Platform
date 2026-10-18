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
 * 工资表（wage_record）：个人月度工资审核流转
 * <p>工资单号规则 WG{YYYYMM}-{工号}，审核时按需生成。</p>
 */
@Data
@TableName("wage_record")
@Schema(description = "工资核算-工资表")
public class WageRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "工资单号 WG{YYYYMM}-{工号}")
    private String wageNo;

    @Schema(description = "员工姓名")
    private String staffName;

    @Schema(description = "工号")
    private String staffNo;

    @Schema(description = "小组id")
    private Long groupId;

    @Schema(description = "小组名称")
    private String groupName;

    @Schema(description = "人员系数")
    private BigDecimal coef;

    @Schema(description = "结算周期 YYYY-MM")
    private String settlePeriod;

    @Schema(description = "计件金额")
    private BigDecimal pieceAmount;

    @Schema(description = "审核状态：待审核/已审核/已驳回")
    private String auditStatus;

    @Schema(description = "审核人")
    private String auditor;

    @Schema(description = "结算/审核日期")
    private LocalDate settleDate;

    @Schema(description = "备注")
    private String remark;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
