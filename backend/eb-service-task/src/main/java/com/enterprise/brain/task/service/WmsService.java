package com.enterprise.brain.task.service;

import com.enterprise.brain.task.config.MaterialProperties;
import com.enterprise.brain.task.wms.dto.WmsPickingSendResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * WMS 对接服务（M7 出库申请 + ProductionPicking 生产领料申请）
 */
@Service
@Slf4j
public class WmsService {

    @Resource
    private MaterialProperties materialProperties;

    @Resource
    private ObjectMapper objectMapper;

    private volatile RestTemplate wmsRestTemplate;

    // ==================== ProductionPicking 生产领料申请 ====================

    /**
     * 发送生产领料申请（ProductionPicking）到 WMS
     *
     * @param bodyData  业务层已组装好的 Data 对象（含 BillCode, ProductionNo, Entries 等）
     * @param lineCode  生产线编码（用于日志）
     * @return WMS 响应；Success=false 或 HTTP 非 2xx 抛 RuntimeException
     */
    public WmsPickingSendResponse sendProductionPicking(Map<String, Object> bodyData, String lineCode) {
        MaterialProperties.Wms wms = materialProperties.getWms();
        String url = wms.getBaseUrl() + wms.getPickingPath();

        // 外层信封
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("RequestId", UUID.randomUUID().toString());
        envelope.put("RequestTime", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        envelope.put("SourceSystem", "MES");
        envelope.put("TargetSystem", "WMS");
        envelope.put("Data", bodyData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(envelope, headers);

        log.info("[WMS] ProductionPicking 请求 lineCode={} url={} body={}", lineCode, url, envelope);

        try {
            ResponseEntity<String> response = getRestTemplate().exchange(url, HttpMethod.POST, entity, String.class);
            String responseBody = response.getBody();
            log.info("[WMS] ProductionPicking 响应: {}", responseBody);

            WmsPickingSendResponse resp = objectMapper.readValue(responseBody, WmsPickingSendResponse.class);

            if (resp.getSuccess() == null || !resp.getSuccess()) {
                throw new RuntimeException("WMS 领料申请失败: " + resp.getMessage());
            }
            return resp;
        } catch (HttpClientErrorException e) {
            // HTTP 4xx 错误（如 409 Conflict），尝试从 response body 解析 WMS 详细错误消息
            String wmsDetail = extractWmsMessage(e.getResponseBodyAsString());
            String msg = wmsDetail != null
                    ? wmsDetail
                    : String.format("HTTP %d %s", e.getStatusCode().value(), e.getStatusText());
            log.error("[WMS] ProductionPicking HTTP 错误: {}", msg);
            throw new RuntimeException("WMS 领料申请失败: " + msg);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("WMS ProductionPicking 调用异常: " + e.getMessage(), e);
        }
    }

    // ==================== M7 出库申请（原有） ====================

    public static class OutboundItem {
        private final String materialCode;
        private final String batchNo;
        private final String locationCode;
        private final java.math.BigDecimal qty;

        public OutboundItem(String materialCode, String batchNo, String locationCode, java.math.BigDecimal qty) {
            this.materialCode = materialCode;
            this.batchNo = batchNo;
            this.locationCode = locationCode;
            this.qty = qty;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialCode", materialCode);
            m.put("batchNo", batchNo);
            m.put("locationCode", locationCode);
            m.put("qty", qty);
            return m;
        }
    }

    public String applyOutbound(String sourceOrderNo, String erpOrderNo, String warehouse,
                                List<OutboundItem> items) {
        MaterialProperties.Wms wms = materialProperties.getWms();
        if (!wms.isEnabled()) {
            log.info("[WMS] 未启用WMS对接，跳过出库申请 sourceOrderNo={}", sourceOrderNo);
            return null;
        }

        String url = wms.getBaseUrl() + wms.getOutboundPath();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceOrderNo", sourceOrderNo);
        body.put("erpOrderNo", erpOrderNo);
        body.put("warehouse", warehouse);
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (OutboundItem item : items) itemList.add(item.toMap());
        body.put("items", itemList);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = getRestTemplate().exchange(url, HttpMethod.POST, entity, String.class);
            String responseBody = response.getBody();
            log.info("[WMS] 出库申请响应: {}", responseBody);

            JsonNode root = objectMapper.readTree(responseBody);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new RuntimeException("WMS出库申请失败: " +
                        (root.has("msg") ? root.get("msg").asText() : responseBody));
            }
            String wmsOrderNo = root.path("data").path("wmsOrderNo").asText(null);
            if (wmsOrderNo == null || wmsOrderNo.isEmpty()) {
                throw new RuntimeException("WMS出库申请响应缺少wmsOrderNo: " + responseBody);
            }
            return wmsOrderNo;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("WMS出库申请调用异常: " + e.getMessage(), e);
        }
    }

    private RestTemplate getRestTemplate() {
        if (wmsRestTemplate == null) {
            synchronized (this) {
                if (wmsRestTemplate == null) {
                    MaterialProperties.Wms wms = materialProperties.getWms();
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(wms.getConnectTimeoutMs());
                    factory.setReadTimeout(wms.getReadTimeoutMs());
                    wmsRestTemplate = new RestTemplate(factory);
                }
            }
        }
        return wmsRestTemplate;
    }

    /**
     * 从 WMS HTTP 错误响应 body 中提取 Message 字段。
     * WMS 返回格式如: {"Code":409,"Success":false,"Message":"物料 xxx 不存在...","Data":{...}}
     */
    private String extractWmsMessage(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode msgNode = root.path("Message");
            if (msgNode.isTextual() && !msgNode.asText().isBlank()) {
                return msgNode.asText();
            }
        } catch (Exception ignored) {}
        return body;  // 解析不了就原样返回 body 内容
    }
}
