package com.enterprise.brain.task.wage.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 金蝶《生产订单》(PRD_MO) 查询行 VO（工资核算模块使用）
 * <p>字段顺序必须与 executeBillQuery 的 FieldKeys 严格对应。</p>
 */
@Data
public class WageMoRowVO {
    private String moBillNo;      // FBillNo 生产订单号
    private String productCode;   // FMaterialID.fnumber 产品物料编码
    private String productName;   // FMaterialID.fname 产品物料名称
    private BigDecimal qty;       // FQty 产品数量（计划数量）
    private BigDecimal yieldQty;  // FYieldQty 完工入库数
    private String status;        // FStatus：1计划 2计划确认 3下达 4开工 5完工 6结案 7结算
    private String workshop;      // FWorkShopID.fname 生产车间
    private String planStartTime; // FPlanStartTime 计划开工时间
    private String planFinishTime;// FPlanFinishTime 计划完工时间
}
