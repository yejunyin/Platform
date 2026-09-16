package com.enterprise.brain.task.service;

import com.enterprise.brain.task.config.WorkOrderPushProperties;
import com.enterprise.brain.task.dto.mq.WorkOrderDTO;
import com.enterprise.brain.task.dto.mq.WorkOrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 设备维修工单 MQ 推送
 * <p>接收前端组织好的 {@link WorkOrderEvent}，校验通过后原样序列化发送至 MQ。</p>
 * <p>RocketMQ（实际使用）：Topic=G-HL-DEVICE，Tag=upsert/delete，参考对接文档 6.2 发送样例。</p>
 * <p>RabbitMQ（备选）：Exchange=agent.exchange(Direct)，RoutingKey=work-order（tag 不参与路由）。</p>
 */
@Slf4j
@Service
public class WorkOrderPushService {

    private static final String OP_UPSERT = "UPSERT";
    private static final String OP_DELETE = "DELETE";

    @Resource
    private WorkOrderPushProperties props;

    /** MQ 消息序列化（字段均为字符串原样透传） */
    private final ObjectMapper mapper = new ObjectMapper();

    private DefaultMQProducer rocketProducer;
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    @PostConstruct
    public void init() {
        WorkOrderPushProperties.Mq mq = props.getMq();
        if (!"rocket".equalsIgnoreCase(mq.getType())) {
            log.info("[工单推送] MQ类型={}，跳过RocketMQ producer初始化", mq.getType());
            return;
        }
        WorkOrderPushProperties.Rocket rocket = mq.getRocket();
        if (rocket.getNameServer() == null || rocket.getNameServer().isBlank()) {
            log.error("[工单推送] 未配置 work-order.push.mq.rocket.name-server，producer 未启动");
            return;
        }
        try {
            rocketProducer = new DefaultMQProducer(rocket.getProducerGroup());
            rocketProducer.setNamesrvAddr(rocket.getNameServer());
            rocketProducer.setSendMsgTimeout(rocket.getSendTimeoutMs());
            rocketProducer.setRetryTimesWhenSendFailed(rocket.getRetryTimes());
            rocketProducer.start();
            log.info("[工单推送] RocketMQ producer 已启动: namesrv={}, group={}",
                    rocket.getNameServer(), rocket.getProducerGroup());
        } catch (Exception e) {
            log.error("[工单推送] RocketMQ producer 启动失败: {}", e.getMessage(), e);
            rocketProducer = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (rocketProducer != null) {
            rocketProducer.shutdown();
        }
        closeRabbitQuietly();
    }

    /**
     * 校验并发送工单事件至 MQ
     *
     * @param event 工单事件
     * @return RocketMQ 消息ID（RabbitMQ 无对应值返回 null）
     */
    public String push(WorkOrderEvent event) throws Exception {
        long start = System.currentTimeMillis();
        log.info("[工单推送] 收到推送请求: op={}, sourceId={}, mqType={}",
                event == null ? null : event.getOp(),
                event == null ? null : event.getSourceId(),
                props.getMq().getType());
        validate(event);
        byte[] body = mapper.writeValueAsBytes(event);
        String tag = OP_UPSERT.equalsIgnoreCase(event.getOp()) ? "upsert" : "delete";
        // 完整消息体落日志，便于排查消费端未收到/字段缺失问题
        log.info("[工单推送] 序列化完成: tag={}, bodySize={} bytes, body={}",
                tag, body.length, new String(body, StandardCharsets.UTF_8));

        WorkOrderPushProperties.Mq mq = props.getMq();
        if ("rocket".equalsIgnoreCase(mq.getType())) {
            String msgId = sendRocket(event, tag, body);
            log.info("[工单推送] RocketMQ通道推送完成: op={}, sourceId={}, msgId={}, 总耗时={}ms",
                    event.getOp(), event.getSourceId(), msgId, System.currentTimeMillis() - start);
            return msgId;
        }
        if ("rabbit".equalsIgnoreCase(mq.getType())) {
            sendRabbit(event, body);
            log.info("[工单推送] RabbitMQ通道推送完成: op={}, sourceId={}, 总耗时={}ms",
                    event.getOp(), event.getSourceId(), System.currentTimeMillis() - start);
            return null;
        }
        log.error("[工单推送] 不支持的MQ类型: {}（work-order.push.mq.type 仅支持 rocket|rabbit）", mq.getType());
        throw new IllegalStateException("不支持的MQ类型: " + mq.getType()
                + "（work-order.push.mq.type 仅支持 rocket|rabbit）");
    }

    /** 校验规则见《设备维修工单MQ推送后端接口文档》4.1 */
    private void validate(WorkOrderEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String op = event.getOp();
        if (op == null || op.isBlank()
                || !(OP_UPSERT.equalsIgnoreCase(op) || OP_DELETE.equalsIgnoreCase(op))) {
            throw new IllegalArgumentException("op 仅支持 UPSERT 或 DELETE");
        }
        if (event.getSourceId() == null || event.getSourceId().isBlank()) {
            throw new IllegalArgumentException("sourceId 不能为空");
        }
        if (OP_UPSERT.equalsIgnoreCase(op)) {
            WorkOrderDTO data = event.getData();
            if (data == null) {
                throw new IllegalArgumentException("op=UPSERT 时 data 不能为空");
            }
            if (data.getId() == null || data.getId().isBlank()) {
                throw new IllegalArgumentException("op=UPSERT 时 data.id 不能为空");
            }
            if (data.getStatus() == null || data.getStatus().isBlank()) {
                throw new IllegalArgumentException("op=UPSERT 时 data.status 不能为空");
            }
        }
    }

    private String sendRocket(WorkOrderEvent event, String tag, byte[] body) throws Exception {
        if (rocketProducer == null) {
            log.error("[工单推送] RocketMQ producer 未启动（检查 name-server 配置与 MQ 连通性），消息未发送: op={}, sourceId={}",
                    event.getOp(), event.getSourceId());
            throw new IllegalStateException("RocketMQ producer 未启动（检查 name-server 配置与 MQ 连通性）");
        }
        WorkOrderPushProperties.Rocket rocket = props.getMq().getRocket();
        Message msg = new Message(rocket.getTopic(), tag, body);
        log.info("[工单推送] 开始发送 RocketMQ: topic={}, tag={}, namesrv={}",
                rocket.getTopic(), tag, rocket.getNameServer());
        long t0 = System.currentTimeMillis();
        try {
            SendResult result = rocketProducer.send(msg);
            log.info("[工单推送] op={} sourceId={} → RocketMQ topic={} tag={} msgId={} sendStatus={} 发送耗时={}ms",
                    event.getOp(), event.getSourceId(), rocket.getTopic(), tag,
                    result.getMsgId(), result.getSendStatus(), System.currentTimeMillis() - t0);
            return result.getMsgId();
        } catch (Exception e) {
            log.error("[工单推送] op={} sourceId={} → RocketMQ 发送失败: topic={}, tag={}, 发送耗时={}ms, 原因={}",
                    event.getOp(), event.getSourceId(), rocket.getTopic(), tag,
                    System.currentTimeMillis() - t0, e.getMessage(), e);
            throw e;
        }
    }

    /** RabbitMQ 备选通道：懒建连接/信道，断开自动重建 */
    private synchronized void sendRabbit(WorkOrderEvent event, byte[] body) throws Exception {
        WorkOrderPushProperties.Rabbit rabbit = props.getMq().getRabbit();
        if (rabbitChannel == null || !rabbitChannel.isOpen()) {
            log.info("[工单推送] 建立RabbitMQ连接: {}:{}@{}", rabbit.getHost(), rabbit.getPort(), rabbit.getExchange());
            closeRabbitQuietly();
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rabbit.getHost());
            factory.setPort(rabbit.getPort());
            factory.setUsername(rabbit.getUsername());
            factory.setPassword(rabbit.getPassword());
            rabbitConnection = factory.newConnection();
            rabbitChannel = rabbitConnection.createChannel();
        }
        long t0 = System.currentTimeMillis();
        try {
            rabbitChannel.basicPublish(rabbit.getExchange(), rabbit.getRoutingKey(), null, body);
            log.info("[工单推送] op={} sourceId={} → RabbitMQ exchange={} routingKey={} 发送耗时={}ms",
                    event.getOp(), event.getSourceId(), rabbit.getExchange(), rabbit.getRoutingKey(),
                    System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("[工单推送] op={} sourceId={} → RabbitMQ 发送失败: exchange={}, routingKey={}, 发送耗时={}ms, 原因={}",
                    event.getOp(), event.getSourceId(), rabbit.getExchange(), rabbit.getRoutingKey(),
                    System.currentTimeMillis() - t0, e.getMessage(), e);
            // 发布失败大概率连接已失效，清空待下次重建
            closeRabbitQuietly();
            throw e;
        }
    }

    private void closeRabbitQuietly() {
        try {
            if (rabbitChannel != null && rabbitChannel.isOpen()) {
                rabbitChannel.close();
            }
        } catch (Exception ignore) {
            // 关闭失败仅忽略，连接销毁时统一回收
        }
        try {
            if (rabbitConnection != null && rabbitConnection.isOpen()) {
                rabbitConnection.close();
            }
        } catch (Exception ignore) {
            // 同上
        }
        rabbitChannel = null;
        rabbitConnection = null;
    }
}
