package com.enterprise.brain.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 设备维修工单 MQ 推送配置
 * <p>前端不感知 MQ 类型/地址/账号，均由后端配置维护。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "work-order.push")
public class WorkOrderPushProperties {

    private Mq mq = new Mq();

    @Data
    public static class Mq {
        /** MQ 类型：rocket（实际使用）| rabbit（备选，消费端切换时启用） */
        private String type = "rocket";
        private Rocket rocket = new Rocket();
        private Rabbit rabbit = new Rabbit();
    }

    /**
     * RocketMQ（实际使用，与消费端部署一致）
     */
    @Data
    public static class Rocket {
        /** NameServer 地址 */
        private String nameServer = "192.168.15.6:9876";
        /** Topic */
        private String topic = "G-HL-DEVICE";
        /** Producer Group */
        private String producerGroup = "andon-work-order-producer";
        /** 同步发送超时（毫秒） */
        private int sendTimeoutMs = 3000;
        /** 同步发送失败重试次数 */
        private int retryTimes = 1;
    }

    /**
     * RabbitMQ（备选）
     */
    @Data
    public static class Rabbit {
        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
        /** Direct 交换机，需提前声明 */
        private String exchange = "agent.exchange";
        /** 等于 topic 值，tag 不参与路由 */
        private String routingKey = "work-order";
    }
}
