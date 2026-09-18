package com.enterprise.brain.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户 (sys_user)
 * <p>质检人员维护界面对应该表：username=工号（唯一），real_name=姓名。</p>
 */
@Data
@TableName("sys_user")
@Schema(description = "系统用户")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    @Schema(description = "用户ID（应用层赋值，表非自增）")
    private Long id;

    @Schema(description = "工号（登录名，唯一）")
    private String username;

    @Schema(description = "密码（BCrypt）")
    private String password;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    /** 数据库列为连续小写 dingdingid（无下划线），需显式指定列名，避免驼峰转下划线后映射为 ding_ding_id */
    @TableField("dingdingid")
    @Schema(description = "钉钉ID")
    private String dingdingId;

    @Schema(description = "状态 1启用 0停用")
    private Integer status;

    @Schema(description = "是否删除 0正常 1已删除（软删除）")
    private Integer deleted;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
