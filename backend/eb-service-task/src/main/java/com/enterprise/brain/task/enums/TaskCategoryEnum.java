package com.enterprise.brain.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务分类枚举
 */
@Getter
@AllArgsConstructor
public enum TaskCategoryEnum {

    LEAVE("LEAVE", "请假"),
    EXPENSE("EXPENSE", "报销"),
    PURCHASE("PURCHASE", "采购"),
    PRODUCTION("PRODUCTION", "生产"),
    QUALITY("QUALITY", "质量"),
    CONTRACT("CONTRACT", "合同"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;

    public static String getDesc(String code) {
        if (code == null) {
            return "其他";
        }
        for (TaskCategoryEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e.getDesc();
            }
        }
        return "其他";
    }
}
