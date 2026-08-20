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
}
