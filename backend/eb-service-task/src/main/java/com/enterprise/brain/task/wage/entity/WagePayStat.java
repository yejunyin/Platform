package com.enterprise.brain.task.wage.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工资统计明细（wage_pay_stat）
 * <p>核算单元 = 生产任务单 × 用料明细 × 成员；系统按开工月份自动生成，可复核。</p>
 */
@Data
@TableName("wage_pay_stat")
@Schema(description = "工资核算-计件统计明细")
public class WagePayStat implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "工资月份 YYYY-MM")
    private String wageMonth;

    @Schema(description = "生产任务单号")
    private String taskNo;

    @Schema(description = "小组id")
    private Long groupId;

    @Schema(description = "小组名称（冗余）")
    private String groupName;

    @Schema(description = "员工姓名")
    private String staffName;

    @Schema(description = "工号")
    private String staffNo;

    @Schema(description = "工种")
    private String jobType;

    @Schema(description = "品名")
    private String productName;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "工价（子项单价）")
    private BigDecimal workPrice;

    @Schema(description = "数量（子项数量）")
    private BigDecimal qty;

    @Schema(description = "个人金额 = 工单组级金额 × 分摊比例（可人工覆盖）")
    private BigDecimal amount;

    @Schema(description = "开工时间")
    private LocalDateTime startTime;

    @Schema(description = "工序")
    private String processName;

    @Schema(description = "子物料代码")
    @TableField("sub_material_code")
    private String materialCode;

    @Schema(description = "子项物料名称")
    private String subMaterialName;

    @Schema(description = "子项数量")
    private BigDecimal subQty;

    @Schema(description = "子项单价")
    private BigDecimal subPrice;

    @Schema(description = "明细金额 = 子项数量 × 子项单价")
    private BigDecimal detailAmount;

    @Schema(description = "工单组级金额（分摊基数）")
    private BigDecimal groupAmount;

    @Schema(description = "人员系数")
    private BigDecimal coef;

    @Schema(description = "分摊比例")
    private BigDecimal shareRatio;

    @Schema(description = "复核人")
    private String reviewer;

    @Schema(description = "复核时间")
    private LocalDateTime reviewTime;

    @Schema(description = "生成操作人")
    private String entryUser;

    @Schema(description = "备注")
    private String remark;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
