package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 质检人员列表项（sys_user：username=工号，real_name=姓名）
 */
@Data
@Schema(description = "质检人员信息")
public class QcStaffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "工号")
    private String username;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "钉钉ID（sys_user.dingdingid）")
    private String dingdingId;

    public QcStaffVO() {
    }

    public QcStaffVO(Long id, String username, String realName) {
        this(id, username, realName, null);
    }

    public QcStaffVO(Long id, String username, String realName, String dingdingId) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.dingdingId = dingdingId;
    }
}
