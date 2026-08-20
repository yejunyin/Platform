package com.enterprise.brain.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 来源系统枚举
 */
@Getter
@AllArgsConstructor
public enum ExternalSystemEnum {

    OA("OA", "协同办公系统"),
    ERP("ERP", "企业资源计划系统"),
    MES("MES", "制造执行系统"),
    EB("EB", "企业大脑平台");

    private final String code;
    private final String desc;

    public static String getDesc(String code) {
        if (code == null) {
            return "未知";
        }
        for (ExternalSystemEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e.getDesc();
            }
        }
        return "未知";
    }
}
