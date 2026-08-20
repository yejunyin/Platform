package com.enterprise.brain.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务优先级枚举
 */
@Getter
@AllArgsConstructor
public enum TaskPriorityEnum {

    URGENT(1, "紧急"),
    HIGH(2, "高"),
    MEDIUM(3, "中"),
    LOW(4, "低");

    private final Integer code;
    private final String desc;

    public static String getDesc(Integer code) {
        if (code == null) {
            return "";
        }
        for (TaskPriorityEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e.getDesc();
            }
        }
        return "未知";
    }
}
