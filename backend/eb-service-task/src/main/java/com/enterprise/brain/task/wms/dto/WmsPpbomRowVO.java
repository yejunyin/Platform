package com.enterprise.brain.task.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 金蝶 PRD_PPBOM（生产用料清单）查询行 VO
 * 用于 WMS 叫料页面：输入生产订单号 → 查出子项物料明细
 *
 * FieldKeys: FBillNo, FMOBillNO, FMaterialID.fnumber, FMaterialID.fname,
 *           FMaterialID2.fnumber, FMaterialID2.fname, FNeedQty2, FUnitID2.fnumber, FUnitID2.fname,
 *           FMTONO
 */
@Data
@Schema(description = "金蝶生产用料清单行（用于WMS叫料查询）")
public class WmsPpbomRowVO {

    @Schema(description = "用料清单编号（PRD_PPBOM.FBillNo）")
    private String ppbomBillNo;

    @Schema(description = "生产订单号（PRD_PPBOM.FMOBillNO，用户输入的查询键）")
    private String productionNo;

    @Schema(description = "产品编码（父项）")
    private String productCode;

    @Schema(description = "产品名称（父项）")
    private String productName;

    @Schema(description = "子项物料编码")
    private String materialCode;

    @Schema(description = "子项物料名称")
    private String materialName;

    @Schema(description = "应发数量（FNeedQty2）")
    private BigDecimal needQty;

    @Schema(description = "单位编码（FUnitID2.fnumber，推 WMS UnitCode 用）")
    private String unitNumber;

    @Schema(description = "单位名称（FUnitID2.fname，仅展示）")
    private String unitName;

    @Schema(description = "计划跟踪号 / 生产用料清单 FMTONO，推送 WMS PlanCode 用")
    private String planCode;

    @Schema(description = "发料仓库（FPickStockId，基础资料属性，显示中文仓库名）")
    private String pickStock;

    // ============ 本地累计数据（前端展示用，后端查询时填充） ============

    @Schema(description = "叫料已推送 WMS 数量（wms_picking_entry.pickedQty 汇总，status=sent）")
    private BigDecimal pushedQty;

    @Schema(description = "定时领料任务中该物料的发料总数量（wms_pick_task_entry.totalQty 汇总，不含已取消任务）")
    private BigDecimal pickTaskTotalQty;

    @Schema(description = "定时领料任务中该物料已发数量（wms_pick_task_entry.sentQty 汇总）")
    private BigDecimal pickTaskSentQty;

    @Schema(description = "剩余可领料数量 = needQty - pushedQty - pickTaskSentQty")
    private BigDecimal remainingQty;

    @Schema(description = "即时库存数量（金蝶 STK_Inventory，FStockId.FNumber='2010' 仓库）")
    private BigDecimal immediateStockQty;
}
