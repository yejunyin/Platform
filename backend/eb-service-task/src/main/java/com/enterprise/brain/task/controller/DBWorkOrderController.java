package com.enterprise.brain.task.controller;

import com.enterprise.brain.task.dto.mq.WorkOrderEvent;
import com.enterprise.brain.task.dto.response.MaterialResult;
import com.enterprise.brain.task.service.WorkOrderPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备维修工单 MQ 推送接口
 * <p>安灯小程序将组织好的 WorkOrderEvent 推送到本服务，由后端转发至 MQ
 * （RocketMQ Topic=G-HL-DEVICE，Tag=upsert/delete），消费端同步至 inv_eqp_maintenance。</p>
 * <p>统一响应 { "status": 0, "msg": "推送成功", "data": null }；status=-1 校验失败或 MQ 发送异常。</p>
 */
@Slf4j
@RestController
@RequestMapping("/DBWorkOrder")
@Tag(name = "设备维修工单推送", description = "安灯小程序工单事件转发MQ（消费端同步设备维修工单表）")
public class DBWorkOrderController {

    @Resource
    private WorkOrderPushService workOrderPushService;

    @PostMapping("/push")
    @Operation(summary = "推送工单事件至MQ", description = "接收WorkOrderEvent，校验通过后原样序列化发送至MQ；"
            + "op=UPSERT携带工单数据，op=DELETE仅携带sourceId；消息契约见《设备维修工单MQ数据同步对接文档》")
    public MaterialResult<Void> push(@RequestBody WorkOrderEvent event) {
        try {
            workOrderPushService.push(event);
            MaterialResult<Void> r = MaterialResult.ok(null);
            r.setMsg("推送成功");
            return r;
        } catch (IllegalArgumentException e) {
            log.warn("[工单推送] 校验失败: {}", e.getMessage());
            return MaterialResult.error(-1, e.getMessage());
        } catch (Exception e) {
            log.error("[工单推送] MQ发送异常", e);
            return MaterialResult.error(-1, "MQ发送异常：" + e.getMessage());
        }
    }

    /** 请求体JSON不可解析时保持本接口组响应格式（status=-1） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public MaterialResult<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[工单推送] 请求体格式错误: {}", e.getMessage());
        return MaterialResult.error(-1, "请求体格式错误，需为合法的WorkOrderEvent JSON");
    }
}
