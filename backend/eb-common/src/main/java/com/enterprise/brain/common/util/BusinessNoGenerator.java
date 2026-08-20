package com.enterprise.brain.common.util;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成工具类
 */
public class BusinessNoGenerator {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 生成任务编号
     * 格式: TK + yyyyMMdd + 6位序号
     */
    public static String generateTaskNo() {
        String dateStr = LocalDateTime.now().format(DATETIME_FORMATTER);
        String suffix = IdUtil.getSnowflakeNextIdStr().substring(12);
        if (suffix.length() > 6) {
            suffix = suffix.substring(suffix.length() - 6);
        }
        suffix = StrUtil.padPre(suffix, 6, '0');
        return "TK" + dateStr + suffix;
    }

    /**
     * 生成指定前缀的业务编号
     *
     * @param prefix 前缀
     */
    public static String generateBizNo(String prefix) {
        String dateStr = LocalDateTime.now().format(DATETIME_FORMATTER);
        String suffix = IdUtil.getSnowflakeNextIdStr().substring(12);
        if (suffix.length() > 6) {
            suffix = suffix.substring(suffix.length() - 6);
        }
        suffix = StrUtil.padPre(suffix, 6, '0');
        return prefix + dateStr + suffix;
    }
}
