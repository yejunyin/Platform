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
 * 单价工序资料（wage_price）：子物料 × 工序 的计件单价
 */
@Data
@TableName("wage_price")
@Schema(description = "工资核算-单价工序资料")
public class WagePrice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "子物料代码")
    private String materialCode;

    @Schema(description = "子项物料名称")
    private String materialName;

    @Schema(description = "工序名称")
    private String processName;

    @Schema(description = "单价（元）")
    private BigDecimal unitPrice;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "失效日期（null=长期有效）")
    private LocalDate expiryDate;

    @Schema(description = "状态：启用/停用")
    private String status;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
