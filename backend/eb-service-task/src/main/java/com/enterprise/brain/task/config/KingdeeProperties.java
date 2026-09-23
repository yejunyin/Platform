package com.enterprise.brain.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 金蝶 K3 Cloud ERP 接口配置
 * <p>从 application.yml 中的 kingdee 配置项加载。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kingdee")
public class KingdeeProperties {

    /** 金蝶 API 基址，如 http://192.168.1.16/k3cloud */
    private String baseUrl;

    /** 登录用户名 */
    private String username;

    /** 登录密码 */
    private String password;

    /** 账套 ID */
    private String acctId;

    /** 语言 ID（2052 简体中文） */
    private String lcid;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 60000;

    /**
     * 生产退料单"分录计划跟踪号与用料清单不一致"交互警告的兜底交互标识（InterationFlags）。
     * 正常情况下标识从当次Save响应中自动提取，无需配置；仅当金蝶补丁版本不在响应中回传标识时，
     * 可在此配置环境实际标识（多个用分号分隔）作为兜底。默认空=不兜底。
     */
    private String mtoInteractionFlag = "";
}
