package com.enterprise.brain.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务操作类型枚举
 */
@Getter
@AllArgsConstructor
public enum TaskActionTypeEnum {

    CREATE("CREATE", "创建"),
    CLAIM("CLAIM", "领取"),
    APPROVE("APPROVE", "同意"),
    REJECT("REJECT", "驳回"),
    TRANSFER("TRANSFER", "转办"),
    DELEGATE("DELEGATE", "委派"),
    URGE("URGE", "催办"),
    COMMENT("COMMENT", "评论"),
    REVOKE("REVOKE", "撤销"),
    READ("READ", "已读");

    private final String code;
    private final String desc;

    public static String getDesc(String code) {
        if (code == null) {
            return "";
        }
        for (TaskActionTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e.getDesc();
            }
        }
        return "未知";
    }
}
