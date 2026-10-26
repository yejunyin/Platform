package com.enterprise.brain.task.wms.picktask.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 定时 WMS 领料任务明细（wms_pick_task_entry）
 */
@Data
@TableName("wms_pick_task_entry")
@Schema(description = "定时领料任务明细")
public class WmsPickTaskEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    private Integer itemNo;

    private String materialCode;

    private String materialName;

    @Schema(description = "金蝶应发数量（参考用）")
    private BigDecimal planQty;

    @Schema(description = "用户指定的发料总数量")
    private BigDecimal totalQty;

    @Schema(description = "该物料每箱数量")
    private BigDecimal perBoxQty;

    private Integer totalRounds;

    private Integer currentRound;

    @Schema(description = "已发数量")
    private BigDecimal sentQty;

    private String unitCode;
    private String planCode;
    private String stationCode;
    private String warehouseCode;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
