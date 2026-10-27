package com.enterprise.brain.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Jackson 全局配置 — 绝对生效版：
 * 1) Long / BigInteger 序列化为 String（避免 JS 端 Number 精度丢失，雪花 ID 19 位 > JS 安全整数范围 2^53-1 ≈ 9e15）
 * 2) LocalDate / LocalDateTime 格式化
 * <p>通过实现 WebMvcConfigurer#configureMessageConverters 直接替换 Spring MVC 的
 * MappingJackson2HttpMessageConverter，绕开 Spring Boot JacksonAutoConfiguration 的各种自动装配分支，
 * 确保 HTTP 响应 JSON 一定走我们的 ObjectMapper。</p>
 */
@Configuration
public class JacksonConfig implements WebMvcConfigurer {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** 构造带 Long→String + 日期格式的 ObjectMapper */
    public static ObjectMapper buildObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(DATE_FORMAT);
        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFmt));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFmt));
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dtFmt));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dtFmt));

        SimpleModule longModule = new SimpleModule("LongToStringModule");
        longModule.addSerializer(Long.class, ToStringSerializer.instance);
        longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        longModule.addSerializer(BigInteger.class, ToStringSerializer.instance);

        return Jackson2ObjectMapperBuilder.json()
                .modules(longModule, javaTimeModule)
                .build();
    }

    /** 兜底 Bean：方便其他需要 ObjectMapper 的地方（如 KingdeeService 内部调用）注入 */
    @Bean
    public ObjectMapper objectMapper() {
        return buildObjectMapper();
    }

    /**
     * 关键！直接替换 Spring MVC 的 Jackson 消息转换器，
     * 确保 HTTP 响应一定用我们配置好的 ObjectMapper。
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 移除 Spring Boot 自动配置的 MappingJackson2HttpMessageConverter
        converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        // 加入我们自己配置好的
        converters.add(new MappingJackson2HttpMessageConverter(buildObjectMapper()));
    }
}
