package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 叫料记录表 (mes_dwd_material_call)
 */
@Data
@TableName("mes_dwd_material_call")
@Schema(description = "叫料记录")
public class MesMaterialCall implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "叫料单号(主键)")
    private Integer id;

    @Schema(description = "任务单号")
    private String taskId;

    @Schema(description = "物料编码")
    private String materialCode;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "需求数量")
    private Integer requiredQty;

    @Schema(description = "叫料数量")
    private Integer callQty;

    @Schema(description = "叫料类型：normal/urgent")
    private String callType;

    @Schema(description = "叫料人")
    private String caller;

    @Schema(description = "叫料时间")
    private LocalDateTime callTime;

    @Schema(description = "叫料状态：pending/delivering/delivered")
    private String status;

    @Schema(description = "响应人")
    private String responder;

    @Schema(description = "响应时间")
    private LocalDateTime responseTime;

    @Schema(description = "送达时间")
    private LocalDateTime deliverTime;

    @Schema(description = "备注")
    private String remark;
}
