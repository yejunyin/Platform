package com.enterprise.brain.task.wms.picktask.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时 WMS 领料任务执行日志（wms_pick_task_log）
 */
@Data
@TableName("wms_pick_task_log")
@Schema(description = "定时领料任务执行日志")
public class WmsPickTaskLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @Schema(description = "第几轮")
    private Integer roundNo;

    @Schema(description = "当次 MES 单号")
    private String billNo;

    @Schema(description = "WMS 返回任务号")
    private String wmsTaskNo;

    @Schema(description = "sent / failed / skipped")
    private String status;

    private String requestId;

    @Schema(description = "消息（成功/失败详情）")
    private String message;

    private LocalDateTime createTime;
}
