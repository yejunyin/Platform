package com.enterprise.brain.task.dto.mq;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 设备维修工单业务数据（MQ 消息 data 部分）
 * <p>字段与消费端 WorkOrderDTO / 目标表 inv_eqp_maintenance 一一对应（驼峰↔下划线）。</p>
 * <p>时间字段统一 yyyy-MM-dd HH:mm:ss 字符串，原样透传不解析。</p>
 * <p>{@code NON_NULL}：未到达的阶段字段前端不传，序列化时不得补 null 覆盖。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "设备维修工单业务数据")
public class WorkOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "工单唯一标识（目标表主键）", example = "24501")
    private String id;

    @Schema(description = "源系统工单ID（幂等键，与顶层sourceId一致）", example = "24501")
    private String sourceId;

    @Schema(description = "设备ID（关联 inv_equipment.id；创建时必填）", example = "林柏装配一线")
    private String equipmentId;

    @Schema(description = "报修人员姓名", example = "叶军营")
    private String reportPersonnelName;

    @Schema(description = "故障报修时间 yyyy-MM-dd HH:mm:ss（创建时必填）", example = "2026-09-15 13:21:15")
    private String faultOccurrenceTime;

    @Schema(description = "故障现象", example = "测试问题")
    private String faultPhenomenon;

    @Schema(description = "开始维修时间（签到时传）")
    private String repairStartTime;

    @Schema(description = "开始搁置时间（搁置时传）")
    private String pendingStartTime;

    @Schema(description = "搁置原因（搁置/重新激活时传）")
    private String pendingReason;

    @Schema(description = "结束搁置时间（重新激活时传）")
    private String pendingEndTime;

    @Schema(description = "故障类型（创建时传）", example = "仓储业务")
    private String faultType;

    @Schema(description = "故障原因（维修完成时传）")
    private String faultCause;

    @Schema(description = "解决办法（维修完成时传）")
    private String solution;

    @Schema(description = "结束维修时间（维修完成时传）")
    private String repairEndTime;

    @Schema(description = "故障修复时间（确认完成时传，仅已完成有值）")
    private String faultEndTime;

    @Schema(description = "维修人员姓名（更新时取登录用户）", example = "李洋")
    private String maintenancePersonnelName;

    @Schema(description = "工单状态（系统标准值：待维修/维修中/搁置中/待确认/已完成）", example = "待维修")
    private String status;

    @Schema(description = "工单创建时间（创建时传）")
    private String createdAt;

    @Schema(description = "工单更新时间（每次都传）")
    private String updatedAt;
}
