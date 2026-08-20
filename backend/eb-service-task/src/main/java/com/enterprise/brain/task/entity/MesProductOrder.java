package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MES 生产订单表（外部只读表，对应 mes_dwd_productOrder）
 * <p>字段命名保持与 MES 原表一致（小写无下划线），不继承 BaseEntity。</p>
 */
@Data
@TableName("mes_dwd_productOrder")
@Schema(description = "MES 生产订单")
public class MesProductOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    @Schema(description = "MES 主键")
    private Long id;

    @Schema(description = "产品类型")
    private String producttype;

    @Schema(description = "开始时间")
    private LocalDateTime starttime;

    @Schema(description = "订单号")
    private String ordercode;

    @Schema(description = "任务单号")
    private String pcode;

    @Schema(description = "机台/产线编码")
    private String machcode;

    @Schema(description = "物料编码")
    private String materialid;

    @Schema(description = "物料/产品名称")
    private String materialname;

    @Schema(description = "数量")
    private Integer total;

    @Schema(description = "规格")
    private String spec;

    @Schema(description = "车间/部门")
    private String dept;

    @Schema(description = "负责人")
    private String staffname;

    @Schema(description = "排程优先级")
    private Integer schedulepriority;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "BOM 同步标志：0=未同步到 mes_dwd_material_detail，1=已同步")
    private String bomflag;
}
