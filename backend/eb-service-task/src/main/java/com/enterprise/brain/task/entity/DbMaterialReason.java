package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 补退料原因字典 (DB_MATERIAL_REASON)
 * <p>reasonType=1 退料类型(金蝶退料单必需, id即金蝶FReturnType枚举值: 1良品退料 2来料不良退料)
 * / reasonType=2 退料原因(业务描述)。</p>
 */
@Data
@TableName("DB_MATERIAL_REASON")
@Schema(description = "补退料原因字典")
public class DbMaterialReason implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "字典ID")
    private Integer id;

    @Schema(description = "类型 1退料类型(金蝶FReturnType枚举值) 2退料原因")
    private String reasonType;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "金蝶退料原因编码(FReturnReason.FNumber, 如TLYY01_SYS), 仅reasonType=1使用")
    private String erpCode;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "是否启用 0/1")
    private Integer enabled;

    @Schema(description = "原因代码")
    private String reasonId;
}
