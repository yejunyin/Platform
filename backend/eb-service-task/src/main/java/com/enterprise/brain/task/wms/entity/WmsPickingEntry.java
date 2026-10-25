package com.enterprise.brain.task.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WMS 领料单明细（wms_picking_entry）
 */
@Data
@TableName("wms_picking_entry")
@Schema(description = "WMS 领料单明细")
public class WmsPickingEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "关联 wms_picking.id")
    private Long pickingId;

    @Schema(description = "WMS Entries[].ItemNo（从 1 开始）")
    private Integer itemNo;

    @Schema(description = "子项物料编码")
    private String materialCode;

    @Schema(description = "子项物料名称")
    private String materialName;

    @Schema(description = "金蝶应发数量")
    private BigDecimal planQty;

    @Schema(description = "实际领料数量")
    private BigDecimal pickedQty;

    @Schema(description = "单位编码")
    private String unitCode;

    @Schema(description = "工序编码")
    private String planCode;

    @Schema(description = "生产工位编码")
    private String stationCode;

    @Schema(description = "出库仓库编码")
    private String warehouseCode;

    @Schema(description = "备注")
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
