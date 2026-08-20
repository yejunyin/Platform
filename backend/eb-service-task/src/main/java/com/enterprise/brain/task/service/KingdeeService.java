package com.enterprise.brain.task.service;

import com.enterprise.brain.task.config.KingdeeProperties;
import com.enterprise.brain.task.entity.MesMaterialDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 金蝶 K3 Cloud WebAPI 调用服务
 * <p>负责：登录 → 拿 SessionId → 调用 ExecuteBillQuery 拉取生产用料清单 (PRD_PPBOM)。</p>
 */
@Service
@Slf4j
public class KingdeeService {

    private static final String LOGIN_PATH =
            "/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc";
    private static final String BILL_QUERY_PATH =
            "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc";

    private static final String FORM_ID = "PRD_PPBOM";
    private static final String FIELD_KEYS =
            "FMOBillNO,FMaterialID2.fnumber,FMaterialID2.fname,FMaterialModel1," +
            "FNeedQty2,FUnitID2.fname,FInventoryQty,FStockLOCID";

    @Resource
    private KingdeeProperties kingdeeProperties;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 金蝶 WebAPI 登录结果
     */
    @Data
    public static class LoginResult {
        /** kdservice-sessionid cookie 值 */
        private final String kdserviceSessionId;
        /** ASP.NET_SessionId cookie 值（来自 Context.SessionId） */
        private final String contextSessionId;

        public LoginResult(String kdserviceSessionId, String contextSessionId) {
            this.kdserviceSessionId = kdserviceSessionId;
            this.contextSessionId = contextSessionId;
        }

        /** 拼装请求 Cookie 头 */
        public String toCookieHeader() {
            return "kdservice-sessionid=" + kdserviceSessionId + "; ASP.NET_SessionId=" + contextSessionId;
        }
    }

    /**
     * 登录金蝶，返回 sessionId 信息
     */
    public LoginResult login() {
        String url = kingdeeProperties.getBaseUrl() + LOGIN_PATH;

        Map<String, Object> body = new HashMap<>();
        body.put("password", kingdeeProperties.getPassword());
        body.put("acctID", kingdeeProperties.getAcctId());
        body.put("lcid", kingdeeProperties.getLcid());
        body.put("username", kingdeeProperties.getUsername());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("金蝶登录失败：HTTP " + response.getStatusCode());
        }

        String responseBody = response.getBody();
        log.info("[Kingdee] 登录响应: {}", truncate(responseBody));

        String kdserviceSessionId = null;
        String contextSessionId = null;

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 1) 优先从响应体取 KDSVCSessionId（金蝶部分版本会在 body 中返回）
            if (root.has("KDSVCSessionId") && !root.get("KDSVCSessionId").isNull()) {
                kdserviceSessionId = root.get("KDSVCSessionId").asText();
            }
            // 2) Context.SessionId
            if (root.has("Context") && root.get("Context").has("SessionId")
                    && !root.get("Context").get("SessionId").isNull()) {
                contextSessionId = root.get("Context").get("SessionId").asText();
            }
        } catch (Exception e) {
            log.warn("[Kingdee] 解析登录响应体异常: {}", e.getMessage());
        }

        // 3) 兜底：从 Set-Cookie 头取
        if (kdserviceSessionId == null || kdserviceSessionId.isEmpty()) {
            kdserviceSessionId = extractCookieValue(response.getHeaders(), "kdservice-sessionid");
        }
        if (contextSessionId == null || contextSessionId.isEmpty()) {
            contextSessionId = extractCookieValue(response.getHeaders(), "ASP.NET_SessionId");
        }

        if (kdserviceSessionId == null || kdserviceSessionId.isEmpty()
                || contextSessionId == null || contextSessionId.isEmpty()) {
            throw new RuntimeException("金蝶登录响应缺少 KDSVCSessionId 或 Context.SessionId，响应："
                    + truncate(responseBody));
        }

        log.info("[Kingdee] 登录成功 kdservice-sessionid={} ASP.NET_SessionId={}",
                truncate(kdserviceSessionId), truncate(contextSessionId));
        return new LoginResult(kdserviceSessionId, contextSessionId);
    }

    /**
     * 调用 ExecuteBillQuery 查询生产用料清单（PRD_PPBOM）
     * @param pcode 任务单号（mes_dwd_productOrder.pcode → FMOBillNO）
     * @param materialid 物料编码（mes_dwd_productOrder.materialid → FMaterialID.fnumber）
     */
    public List<MesMaterialDetail> queryBom(String pcode, String materialid, LoginResult loginResult) {
        String url = kingdeeProperties.getBaseUrl() + BILL_QUERY_PATH;

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("FormId", FORM_ID);
        parameters.put("FieldKeys", FIELD_KEYS);
        parameters.put("FilterString", String.format(
                "FMaterialID.fnumber='%s' and FMOBillNO='%s'", materialid, pcode));

        Map<String, Object> body = new HashMap<>();
        body.put("format", "1");
        body.put("useragent", "ApiClient");
        body.put("rid", "356831840");
        body.put("parameters", Collections.singletonList(parameters));
        body.put("timestamp", "2022-01-04 13:30:213");
        body.put("v", "1.0");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, loginResult.toCookieHeader());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("金蝶 ExecuteBillQuery 调用失败：HTTP " + response.getStatusCode());
        }

        String responseBody = response.getBody();
        log.info("[Kingdee] ExecuteBillQuery 响应: {}", truncate(responseBody));
        return parseBomResponse(responseBody, pcode);
    }

    /**
     * 解析 ExecuteBillQuery 响应；FieldKeys 顺序对应：
     * 0:FMOBillNO 1:FMaterialID2.fnumber 2:FMaterialID2.fname 3:FMaterialModel1
     * 4:FNeedQty2 5:FUnitID2.fname 6:FInventoryQty 7:FStockLOCID
     */
    private List<MesMaterialDetail> parseBomResponse(String responseBody, String taskId) {
        List<MesMaterialDetail> result = new ArrayList<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new RuntimeException("解析金蝶 ExecuteBillQuery 响应失败: " + e.getMessage(), e);
        }

        JsonNode rowsNode = null;
        // 形式 1: 直接是数组 [[...], [...]]
        if (root.isArray()) {
            rowsNode = root;
        }
        // 形式 2: { "value": [[...]] }
        else if (root.has("value") && root.get("value").isArray()) {
            rowsNode = root.get("value");
        }
        // 形式 3: { "Result": [[...]] }
        else if (root.has("Result") && root.get("Result").isArray()) {
            rowsNode = root.get("Result");
        }
        // 形式 4: 错误响应 { "Result": { "ResponseStatus": { "Errors": [...] } } }
        else if (root.has("Result") && root.get("Result").isObject()) {
            JsonNode status = root.get("Result").get("ResponseStatus");
            if (status != null && status.has("Errors")) {
                StringBuilder errMsg = new StringBuilder();
                for (JsonNode err : status.get("Errors")) {
                    if (errMsg.length() > 0) errMsg.append("; ");
                    errMsg.append(err.has("Message") ? err.get("Message").asText() : err.toString());
                }
                throw new RuntimeException("金蝶查询返回错误: " + errMsg);
            }
            throw new RuntimeException("金蝶查询响应结构无法识别: " + truncate(responseBody));
        } else {
            throw new RuntimeException("金蝶查询响应格式无法识别: " + truncate(responseBody));
        }

        for (JsonNode row : rowsNode) {
            if (!row.isArray() || row.size() < 7) continue;

            MesMaterialDetail detail = new MesMaterialDetail();
            // FMOBillNO 理论上等于 pcode；为保险统一用入参 taskId
            detail.setTaskId(taskId);
            detail.setMaterialCode(textOf(row.get(1)));
            detail.setMaterialName(textOf(row.get(2)));
            detail.setSpecification(textOf(row.get(3)));
            detail.setRequiredQty(parseQty(row.get(4)));
            detail.setUnit(textOf(row.get(5)));
            detail.setAvailableQty(parseQty(row.get(6)));
            detail.setStorageLocation(row.size() > 7 ? textOf(row.get(7)) : null);

            // 默认值
            detail.setPreparedQty(0);
            detail.setStatus("pending");
            detail.setPreparer(null);
            detail.setPrepareTime(null);
            detail.setRemark(null);

            result.add(detail);
        }
        return result;
    }

    private String textOf(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String s = node.asText();
        return s == null || s.isEmpty() || "null".equals(s) ? null : s;
    }

    private Integer parseQty(JsonNode node) {
        if (node == null || node.isNull()) return 0;
        if (node.isNumber()) return node.asInt();
        try {
            String s = node.asText();
            if (s == null || s.isEmpty()) return 0;
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractCookieValue(HttpHeaders headers, String cookieName) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return null;
        String prefix = cookieName + "=";
        for (String c : cookies) {
            if (c == null) continue;
            int idx = c.indexOf(prefix);
            if (idx >= 0) {
                int start = idx + prefix.length();
                int end = c.indexOf(';', start);
                return end == -1 ? c.substring(start) : c.substring(start, end);
            }
        }
        return null;
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) + "...(truncated)" : s;
    }
}
