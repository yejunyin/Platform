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
 * 生产用料清单（wage_material）：每条丝印/移印物料一行，自带完整业务状态
 */
@Data
@TableName("wage_material")
@Schema(description = "工资核算-生产用料清单（含工序+业务状态）")
public class WageMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "生产任务单号")
    private String taskNo;

    @Schema(description = "投料单号")
    private String feedNo;

    @Schema(description = "物料代码")
    private String materialCode;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "数量")
    private BigDecimal qty;

    @Schema(description = "工序名称（丝印/移印）")
    private String processName;

    @Schema(description = "单价（冗余缓存，按工序匹配带出）")
    private BigDecimal unitPrice;

    // ---- 明细级业务状态（一个任务单多条物料各自独立） ----
    @Schema(description = "加工组别 wage_group.id")
    private Long groupId;

    @Schema(description = "加工组名称（虚拟字段，LEFT JOIN wage_group 带出）")
    @TableField(exist = false)
    private String groupName;

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

    @Schema(description = "完工入库数")
    private BigDecimal stockInQty;

    @Schema(description = "结算状态：待结算/已提交/已结算")
    private String settleStatus;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
