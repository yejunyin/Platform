package com.enterprise.brain.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务类型枚举
 */
@Getter
@AllArgsConstructor
public enum TaskTypeEnum {

    APPROVAL("APPROVAL", "审批"),
    NOTICE("NOTICE", "通知"),
    TODO("TODO", "待办"),
    REVIEW("REVIEW", "审核");

    private final String code;
    private final String desc;

    public static String getDesc(String code) {
        if (code == null) {
            return "";
        }
        for (TaskTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e.getDesc();
            }
        }
        return "未知";
    }
}
