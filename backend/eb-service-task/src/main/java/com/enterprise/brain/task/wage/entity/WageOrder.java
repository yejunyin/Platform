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
 * 生产任务单（wage_order）：单据头，只存产品级信息
 * <p>业务状态/组别/时间/结算等明细级属性已下沉到 wage_material</p>
 */
@Data
@TableName("wage_order")
@Schema(description = "工资核算-生产任务单（单据头）")
public class WageOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "生产任务单号（=MES pcode）")
    private String taskNo;

    @Schema(description = "投料单号")
    private String feedNo;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "物料代码")
    @TableField("sub_material_code")
    private String materialCode;

    @Schema(description = "产品数量")
    private BigDecimal productQty;

    @Schema(description = "生产车间")
    private String workshop;

    @Schema(description = "最近同步时间")
    private LocalDateTime syncTime;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
