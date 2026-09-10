package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补退料批次匹配表 (DB_MATERIAL_CALL_BATCH) —— FIFO匹配结果 + 库存预占
 */
@Data
@TableName("DB_MATERIAL_CALL_BATCH")
@Schema(description = "补退料批次匹配(FIFO结果+库存预占)")
public class DbMaterialCallBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "批次匹配ID")
    private String id;

    @Schema(description = "关联申请单ID")
    private String callId;

    @Schema(description = "关联明细ID")
    private String callItemId;

    @Schema(description = "物料编码")
    private String materialCode;

    @Schema(description = "批次号")
    private String batchNo;

    @Schema(description = "库位(账套未启用库位管理时为空)")
    private String locationCode;

    @Schema(description = "仓库名称")
    private String warehouse;

    @Schema(description = "仓库编码(金蝶FStockId.FNumber)")
    private String warehouseCode;

    @Schema(description = "库存组织编码(金蝶FStockOrgId.FNumber)")
    private String stockOrg;

    @Schema(description = "仓位值组合内码(金蝶FStockLocId，启用仓位管理的仓库)")
    private Long locationId;

    @Schema(description = "匹配数量")
    private BigDecimal qty;

    @Schema(description = "仓库是否启用WMS 0/1")
    private Integer wmsFlag;

    @Schema(description = "库存预占标记 0/1")
    private Integer reserved;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
