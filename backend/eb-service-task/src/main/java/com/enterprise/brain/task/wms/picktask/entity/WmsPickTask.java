package com.enterprise.brain.task.wms.picktask.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时 WMS 领料任务主表（wms_pick_task）
 */
@Data
@TableName("wms_pick_task")
@Schema(description = "定时 WMS 领料任务")
public class WmsPickTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "MES 任务单号")
    private String taskNo;

    @Schema(description = "生产订单号")
    private String productionNo;

    @Schema(description = "产品编码")
    private String productCode;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "生产线编码（WMS LineCode）")
    private String lineCode;

    @Schema(description = "组织编码")
    private String orgCode;

    @Schema(description = "部门编码")
    private String deptCode;

    @Schema(description = "每箱数量（循环推送步长）")
    private BigDecimal perBoxQty;

    @Schema(description = "间隔秒数")
    private Integer intervalSec;

    @Schema(description = "状态：pending/running/paused/completed/cancelled/failed")
    private String status;

    @Schema(description = "总轮次")
    private Integer totalRounds;

    @Schema(description = "当前已执行轮次")
    private Integer currentRound;

    private LocalDateTime nextFireTime;
    private LocalDateTime lastFireTime;

    private Integer successCount;
    private Integer failCount;

    @Schema(description = "最近一条消息")
    private String message;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;

    @TableField(exist = false)
    @Schema(description = "任务明细")
    private List<WmsPickTaskEntry> entries;
}
