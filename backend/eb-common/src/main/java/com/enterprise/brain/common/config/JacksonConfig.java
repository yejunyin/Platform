package com.enterprise.brain.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局配置：
 * 1) Long / BigInteger 序列化为 String（避免 JS 端 Number 精度丢失，雪花 ID 19 位 > JS 安全整数范围）
 * 2) LocalDate / LocalDateTime 格式化
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // Long → String
            SimpleModule longModule = new SimpleModule();
            longModule.addSerializer(Long.class, ToStringSerializer.instance);
            longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            longModule.addSerializer(BigInteger.class, ToStringSerializer.instance);
            builder.modules(longModule);

            // 日期格式
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(DATE_FORMAT);
            DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern(DATETIME_FORMAT);

            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFmt));
            javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFmt));
            javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dtFmt));
            javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dtFmt));
            builder.modules(javaTimeModule);
        };
    }
}
