package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 补料原因字典 (DB_MATERIAL_REASON)
 */
@Data
@TableName("DB_MATERIAL_REASON")
@Schema(description = "补料原因字典")
public class DbMaterialReason implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "原因ID")
    private Integer id;

    @Schema(description = "原因类型 1补料原因(预留扩展)")
    private Integer reasonType;

    @Schema(description = "原因名称")
    private String name;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "是否启用 0/1")
    private Integer enabled;
}
