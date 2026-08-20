package com.enterprise.brain.task.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置
 * <p>用于调用金蝶 K3 Cloud WebAPI。</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(KingdeeProperties kingdeeProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(kingdeeProperties.getConnectTimeoutMs());
        factory.setReadTimeout(kingdeeProperties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
