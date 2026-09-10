package com.enterprise.brain.task.service;

import com.enterprise.brain.task.config.MaterialProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 补退料消息推送服务（M8）
 * <p>钉钉工作通知；未配置 webhook 时降级为日志记录，确保流程不因推送失败而中断。</p>
 */
@Service
@Slf4j
public class MaterialMessageService {

    @Resource
    private MaterialProperties materialProperties;

    @Resource
    private RestTemplate restTemplate;

    /**
     * 推送文本消息（markdown卡片）
     * @param scene 场景：submit/audit-pass/audit-reject/erp-error/wms-error
     * @param receiver 接收人标识
     * @param content 消息内容
     */
    public void push(String scene, String receiver, String content) {
        log.info("[MaterialMsg] scene={} receiver={} content={}", scene, receiver, content);
        MaterialProperties.DingTalk dingTalk = materialProperties.getDingTalk();
        if (!dingTalk.isEnabled() || dingTalk.getWebhook() == null || dingTalk.getWebhook().isEmpty()) {
            log.info("[MaterialMsg] 钉钉未配置webhook，消息已降级为日志记录");
            return;
        }
        try {
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", "补退料通知");
            markdown.put("text", "### 补退料通知\n\n" + content);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String resp = restTemplate.postForObject(dingTalk.getWebhook(), entity, String.class);
            log.info("[MaterialMsg] 钉钉推送响应: {}", resp);
        } catch (Exception e) {
            // 推送失败降级为日志，不阻断业务流程
            log.warn("[MaterialMsg] 钉钉推送失败(已降级为日志): {}", e.getMessage());
        }
    }
}
