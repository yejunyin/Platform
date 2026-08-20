package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 备料明细表 (mes_dwd_material_detail)
 */
@Data
@TableName("mes_dwd_material_detail")
@Schema(description = "备料明细")
public class MesMaterialDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;

    @Schema(description = "任务单号")
    private String taskId;

    @Schema(description = "物料编码")
    private String materialCode;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "规格型号")
    private String specification;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "需求数量")
    private Integer requiredQty;

    @Schema(description = "可用库存")
    private Integer availableQty;

    @Schema(description = "已备数量")
    private Integer preparedQty;

    @Schema(description = "库位")
    private String storageLocation;

    @Schema(description = "备料状态：pending/preparing/ready/shortage")
    private String status;

    @Schema(description = "备料人")
    private String preparer;

    @Schema(description = "备料时间")
    private LocalDateTime prepareTime;

    @Schema(description = "备注")
    private String remark;
}
