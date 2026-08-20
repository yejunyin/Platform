package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物料预警表 (mes_dwd_material_alert)
 */
@Data
@TableName("mes_dwd_material_alert")
@Schema(description = "物料预警")
public class MesMaterialAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;

    @Schema(description = "任务单号")
    private String taskId;

    @Schema(description = "预警类型：shortage/low_stock/overtime")
    private String type;

    @Schema(description = "预警级别：danger/warning/info")
    private String level;

    @Schema(description = "预警信息")
    private String message;

    @Schema(description = "预警状态：active/resolved")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
