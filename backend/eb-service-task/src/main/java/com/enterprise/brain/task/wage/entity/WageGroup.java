package com.enterprise.brain.task.wage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人员小组（wage_group）
 */
@Data
@TableName("wage_group")
@Schema(description = "工资核算-人员小组")
public class WageGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "小组名称")
    private String groupName;

    @Schema(description = "组长工号")
    private String leaderNo;

    @Schema(description = "默认工序（丝印/移印）")
    private String defaultProcess;

    @Schema(description = "组内在职成员系数和（冗余自动回写）")
    private BigDecimal coefSum;

    @Schema(description = "组内在职成员数（冗余自动回写）")
    private Integer memberCount;

    @Schema(description = "备注")
    private String remark;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
