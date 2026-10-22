package com.enterprise.brain.task.wage.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 金蝶《生产用料清单》(PRD_PPBOM) 专项查询行 VO（工资核算模块使用）
 * <p>用于按子项物料名称关键词（如丝印/移印）+ 创建日期范围 查询用料清单。</p>
 */
@Data
public class WagePpbomRowVO {
    private String ppbomBillNo;     // FBillNo 用料清单编号
    private String moBillNo;        // FMOBillNO 生产订单号
    private String productCode;     // FMaterialID.fnumber 父项产品物料编码
    private String productName;     // FMaterialID.fname 父项产品物料名称
    private String materialCode;    // FMaterialID2.fnumber 子项物料编码
    private String materialName;    // FMaterialID2.fname 子项物料名称
    private String model;           // FMaterialModel1 规格型号
    private BigDecimal needQty;     // FNeedQty2 应发数量
    private String unitName;        // FUnitID2.fname 单位
    private String createDate;      // FCreateDate 创建日期
}
