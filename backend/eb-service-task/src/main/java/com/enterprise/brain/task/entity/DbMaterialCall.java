package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 补退料申请主表 (DB_MATERIAL_CALL)
 */
@Data
@TableName("DB_MATERIAL_CALL")
@Schema(description = "补退料申请单")
public class DbMaterialCall implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态机常量 */
    public static final int STATUS_PENDING_AUDIT = 10;
    public static final int STATUS_REJECTED = 11;
    public static final int STATUS_MATCHING = 20;
    public static final int STATUS_ERP_CREATING = 30;
    public static final int STATUS_ERP_ERROR = 31;
    public static final int STATUS_ERP_CREATED = 40;
    public static final int STATUS_WMS_ERROR = 41;
    public static final int STATUS_WMS_CREATED = 50;
    public static final int STATUS_FINISHED = 99;

    /** 库存预占中的状态集合（FIFO匹配后尚未闭环，需扣减可用库存） */
    public static final int[] RESERVED_STATUSES = {20, 30, 31, 40, 41, 50};

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "申请单ID")
    private String id;

    @Schema(description = "申请单号 TL+时间戳")
    private String callNo;

    @Schema(description = "申请人ID")
    private String applicantId;

    @Schema(description = "申请人工号(staffCode)")
    private String applicantCode;

    @Schema(description = "申请人姓名")
    private String applicantName;

    @Schema(description = "申请人部门")
    private String applicantDept;

    @Schema(description = "质检员ID")
    private String qcStaffId;

    @Schema(description = "质检员工号")
    private String qcStaffCode;

    @Schema(description = "质检员姓名")
    private String qcStaffName;

    @Schema(description = "归属组织(华丽/桐琴/无刷), 发起人提交时选择, 金蝶退料单头 Forg")
    private String forg;

    @Schema(description = "组别, 发起人提交时手填, 金蝶退料单头 FGroup")
    private String fGroup;

    @Schema(description = "退料原因ID(字典reasonType=2)")
    private String reasonId;

    @Schema(description = "退料原因文本")
    private String reasonText;

    @Schema(description = "状态: 10待质检审核 11已驳回 20批次匹配中 30退料单生成中 31退料单生成异常 40退料单已生成 41WMS申请异常 50WMS申请已生成 99已完成")
    private Integer status;

    @Schema(description = "退料类型字典ID(reasonType=1): 发起人提交时写入(接口5), 审核通过时以接口9回传值为准覆盖")
    private String returnType;

    @Schema(description = "退料类型文案(冗余, 供接口8/7直接返回展示)")
    private String returnTypeName;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "审核人")
    private String auditBy;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "金蝶退料单号")
    private String erpOrderNo;

    @Schema(description = "金蝶补料单号")
    private String erpReplenishOrderNo;

    @Schema(description = "WMS出库申请单号")
    private String wmsOrderNo;

    @Schema(description = "异常信息(状态31/41)")
    private String errorMsg;

    @Schema(description = "是否涉及WMS仓库 0/1")
    private Integer wmsEnabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
