package com.enterprise.brain.task.wage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工序基础资料（wage_process）
 */
@Data
@TableName("wage_process")
@Schema(description = "工资核算-工序基础资料")
public class WageProcess implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "工序编号")
    private String processCode;

    @Schema(description = "工序名称（单价资料下拉来源）")
    private String processName;

    @Schema(description = "所属车间")
    private String workshop;

    @Schema(description = "备注")
    private String remark;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
