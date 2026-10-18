package com.enterprise.brain.task.wage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资汇总行：paystat 按 工资月份 + 工号 归并，审核状态取自 wage_record
 */
@Data
@Schema(description = "工资核算-工资汇总行")
public class WageSummaryVO {

    @Schema(description = "工资月份 YYYY-MM")
    private String wageMonth;

    @Schema(description = "工号")
    private String staffNo;

    @Schema(description = "员工姓名")
    private String staffName;

    @Schema(description = "小组id")
    private Long groupId;

    @Schema(description = "小组名称")
    private String groupName;

    @Schema(description = "工种")
    private String jobType;

    @Schema(description = "明细条数")
    private Integer detailCount;

    @Schema(description = "计件金额合计（个人金额求和）")
    private BigDecimal pieceAmount;

    @Schema(description = "工资表审核状态：未生成/待审核/已审核/已驳回")
    private String auditStatus;

    @Schema(description = "工资单号 WG{YYYYMM}-{工号}")
    private String wageNo;
}
