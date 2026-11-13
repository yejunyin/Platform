package com.enterprise.brain.task.wms.oneclickpick.entity;

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
 * 一键领料任务明细（复用 wms_pick_task_entry）
 */
@Data
@TableName("wms_pick_task_entry")
@Schema(description = "一键领料任务明细")
public class WmsOneClickPickEntry implements Serializable {
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

    @Schema(description = "已发数量（以 WMS 料架释放确认为准）")
    private BigDecimal sentQty;

    @Schema(description = "已推送给 WMS 的次数（轮询均衡用，少的优先推）")
    private Integer sentTimes;

    @Schema(description = "WMS 料架释放成功次数（料架上有→无）")
    private Integer releasedTimes;

    @Schema(description = "WMS 料架确认上料次数（料架上无→有）")
    private Integer confirmedTimes;

    /** 计算属性：在途次数 = 已推 - 已释放（WMS 那边还没领走的） */
    public int getInFlightTimes() {
        int s = sentTimes == null ? 0 : sentTimes;
        int r = releasedTimes == null ? 0 : releasedTimes;
        return s - r;
    }

    @Schema(description = "上次查料架时料架上该物料的数量（null=未查过）")
    private BigDecimal lastOnShelfQty;

    @Schema(description = "最近一次料架确认时间")
    private LocalDateTime lastConfirmedTime;

    private String unitCode;
    private String planCode;
    private String itemId;
    private String stationCode;
    private String warehouseCode;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
