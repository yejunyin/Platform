package com.enterprise.brain.task.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * WMS 领料单头（wms_picking）
 * 生产领料申请发送记录
 */
@Data
@TableName("wms_picking")
@Schema(description = "WMS 领料单头")
public class WmsPicking implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "MES 侧单号")
    private String billNo;

    @Schema(description = "生产订单号（WORKxxxxxx）")
    private String productionNo;

    @Schema(description = "产品编码")
    private String productCode;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "生产线编码（用户手工输入）")
    private String lineCode;

    @Schema(description = "组织编码")
    private String orgCode;

    @Schema(description = "部门编码")
    private String deptCode;

    @Schema(description = "状态：pending/sent/failed")
    private String status;

    @Schema(description = "WMS 返回任务号")
    private String wmsTaskNo;

    @Schema(description = "成功/失败消息")
    private String message;

    @Schema(description = "发给 WMS 的 RequestId")
    private String requestId;

    private LocalDateTime sendTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;

    @TableField(exist = false)
    @Schema(description = "领料明细行（前端展开查看）")
    private List<WmsPickingEntry> entries;
}
