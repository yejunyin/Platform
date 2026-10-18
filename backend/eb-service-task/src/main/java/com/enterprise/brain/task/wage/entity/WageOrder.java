package com.enterprise.brain.task.wage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产任务单（wage_order）：工资核算口径任务单，由 MES/金蝶同步，叠加核算状态
 */
@Data
@TableName("wage_order")
@Schema(description = "工资核算-生产任务单")
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

    @Schema(description = "子物料代码（单价匹配键）")
    private String subMaterialCode;

    @Schema(description = "子项物料名称")
    private String subMaterialName;

    @Schema(description = "产品数量")
    private BigDecimal productQty;

    @Schema(description = "工序名称（丝印/移印）")
    private String processName;

    @Schema(description = "生产车间")
    private String workshop;

    @Schema(description = "单价（同步时按字典带出）")
    private BigDecimal unitPrice;

    @Schema(description = "加工组别 wage_group.id")
    private Long groupId;

    @Schema(description = "开工确认状态：未确认/已确认")
    private String confirmStatus;

    @Schema(description = "业务状态：计划/开工/完工")
    private String bizStatus;

    @Schema(description = "开工时间")
    private LocalDateTime startTime;

    @Schema(description = "完工确认人")
    private String finishUser;

    @Schema(description = "完工数量")
    private BigDecimal finishQty;

    @Schema(description = "完工时间")
    private LocalDateTime finishTime;

    @Schema(description = "完工入库数（金蝶回写）")
    private BigDecimal stockInQty;

    @Schema(description = "结算状态：待结算/已提交/已结算")
    private String settleStatus;

    @Schema(description = "最近同步时间")
    private LocalDateTime syncTime;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
