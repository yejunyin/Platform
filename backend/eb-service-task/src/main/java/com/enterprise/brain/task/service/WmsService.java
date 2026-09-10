package com.enterprise.brain.task.service;

import com.enterprise.brain.task.config.MaterialProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * WMS 出库申请对接服务（M7）
 * <p>仅当批次匹配结果中目标仓库启用WMS时触发；接口失败不阻塞ERP侧流程（独立标记状态41）。</p>
 */
@Service
@Slf4j
public class WmsService {

    @Resource
    private MaterialProperties materialProperties;

    @Resource
    private ObjectMapper objectMapper;

    private volatile RestTemplate wmsRestTemplate;

    /**
     * 出库申请条目
     */
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

    /**
     * 调用WMS生成出库申请，返回 wmsOrderNo
     * @throws RuntimeException WMS调用失败或响应 code != 0
     */
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
}
