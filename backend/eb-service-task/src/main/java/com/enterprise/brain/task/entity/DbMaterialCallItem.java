package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补退料申请明细表 (DB_MATERIAL_CALL_ITEM)
 */
@Data
@TableName("DB_MATERIAL_CALL_ITEM")
@Schema(description = "补退料申请物料明细")
public class DbMaterialCallItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "明细ID")
    private String id;

    @Schema(description = "关联申请单ID")
    private String callId;

    @Schema(description = "生产订单号")
    private String orderCode;

    @Schema(description = "物料编码")
    private String materialCode;

    @Schema(description = "物料名称")
    private String materialName;

    @Schema(description = "规格型号")
    private String spec;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "申请数量")
    private BigDecimal qty;

    /**
     * 物料行备注，对应金蝶 K3Cloud 生产退料单分录备注字段。
     * 列名无下划线，需显式 @TableField（map-underscore-to-camel-case=true 会把 fentrtyMemo 映射成 FENTRTY_MEMO）
     */
    @TableField("FEntrtyMemo")
    @Schema(description = "物料行备注，对应金蝶生产退料单分录备注")
    private String fentrtyMemo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
