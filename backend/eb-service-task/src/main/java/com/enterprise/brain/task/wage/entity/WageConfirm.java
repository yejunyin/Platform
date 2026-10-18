package com.enterprise.brain.task.wage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 开工确认记录（wage_confirm）：开工提交时写入留痕
 */
@Data
@TableName("wage_confirm")
@Schema(description = "工资核算-开工确认记录")
public class WageConfirm implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    @Schema(description = "生产任务单号")
    private String taskNo;

    @Schema(description = "子物料代码")
    private String subMaterialCode;

    @Schema(description = "子项物料名称")
    private String subMaterialName;

    @Schema(description = "确认组别 wage_group.id")
    private Long groupId;

    @Schema(description = "工序名称")
    private String processName;

    @Schema(description = "确认数量")
    private BigDecimal confirmQty;

    @Schema(description = "ERP数量")
    private BigDecimal erpQty;

    @Schema(description = "确认人工号")
    private String confirmerNo;

    @Schema(description = "确认时间")
    private LocalDateTime confirmTime;

    @Schema(description = "确认状态：已确认/已取消")
    private String confirmStatus;

    @Schema(description = "备注")
    private String remark;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
