package com.enterprise.brain.task.wage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产用料清单（wage_material）：开工提交时由金蝶《生产用料清单》下发
 */
@Data
@TableName("wage_material")
@Schema(description = "工资核算-生产用料清单")
public class WageMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "生产任务单号")
    private String taskNo;

    @Schema(description = "投料单号")
    private String feedNo;

    @Schema(description = "业务状态（随任务单联动）：计划/开工/完工")
    private String bizStatus;

    @Schema(description = "物料代码")
    private String materialCode;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "数量")
    private BigDecimal qty;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
