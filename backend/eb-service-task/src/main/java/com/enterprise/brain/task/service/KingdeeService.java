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

import java.math.BigDecimal;
import java.util.*;

/**
 * 金蝶 K3 Cloud WebAPI 调用服务
 * <p>负责：登录（会话缓存）→ ExecuteBillQuery 查询 / View 加载 / Save-Submit-Audit 单据操作。</p>
 * <p>补退料扩展：生产订单(PRD_MO)、用料清单(PRD_PPBOM)、即时库存(STK_Inventory)查询，
 * 生产退料单(PRD_ReturnMtrl)与生产补料单(PRD_FeedMtrl)生成（均含用料清单上查关联）。</p>
 */
@Service
@Slf4j
public class KingdeeService {

    private static final String LOGIN_PATH =
            "/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc";
    private static final String BILL_QUERY_PATH =
            "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc";
    private static final String VIEW_PATH =
            "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc";
    private static final String SAVE_PATH =
            "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Save.common.kdsvc";
    private static final String SUBMIT_PATH =
            "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Submit.common.kdsvc";
    private static final String AUDIT_PATH =
            "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Audit.common.kdsvc";

    private static final String FORM_ID = "PRD_PPBOM";
    private static final String FIELD_KEYS =
            "FMOBillNO,FMaterialID2.fnumber,FMaterialID2.fname,FMaterialModel1," +
            "FNeedQty2,FUnitID2.fname,FInventoryQty,FStockLOCID";

    /** 会话缓存有效期（毫秒），金蝶会话默认30分钟，取20分钟提前失效 */
    private static final long SESSION_TTL_MS = 20 * 60 * 1000L;

    @Resource
    private KingdeeProperties kingdeeProperties;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /** 缓存的登录会话 */
    private volatile LoginResult cachedLogin;
    private volatile long loginTime = 0L;

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
     * 金蝶业务异常（响应体中的 Errors）
     */
    public static class KingdeeApiException extends RuntimeException {
        public KingdeeApiException(String message) {
            super(message);
        }
    }

    /**
     * 单据生成阶段异常：Save成功但Submit/Audit失败时携带已生成单号，供重试续传避免重复建单
     */
    public static class BillStageException extends KingdeeApiException {
        private final transient String formId;
        private final transient String billNo;

        public BillStageException(String formId, String billNo, String message) {
            super(message);
            this.formId = formId;
            this.billNo = billNo;
        }

        /** 单据FormId（PRD_ReturnMtrl退料单 / PRD_FeedMtrl补料单） */
        public String getFormId() {
            return formId;
        }

        /** 已保存的单号（Save阶段失败时为null） */
        public String getBillNo() {
            return billNo;
        }
    }

    /**
     * 生产订单信息（PRD_MO）
     */
    @Data
    public static class MoInfo {
        private Long id;                // FID
        private String billNo;          // FBillNo
        private String date;            // FDate
        private String planFinishDate;  // FPlanFinishDate
        private BigDecimal qty;         // FQty 计划数量
        private BigDecimal rptFinishQty;// FRptFinishQty 已完工汇报数量
        private String status;          // FStatus: 1计划 2计划确认 3下达 4开工 5完工 6结案 7结算
        private String productCode;     // FMaterialId.FNumber
        private String productName;     // FMaterialId.FName
        private String prdOrgNumber;    // FPrdOrgId.FNumber
    }

    /**
     * 用料清单行（PRD_PPBOM）
     */
    @Data
    public static class PpbomRow {
        private Long ppbomId;          // FID 用料清单表头内码（补/退料单上查关联用）
        private String ppbomBillNo;    // FBillNo 用料清单编号
        private String moBillNo;
        private String materialCode;
        private String materialName;
        private String model;           // 规格型号
        private BigDecimal needQty;     // FNeedQty2 应发数量
        private BigDecimal pickedQty;   // FPickedQty 已领数量
        private String unitNumber;      // FUnitID2.fnumber
        private String unitName;        // FUnitID2.fname
        private String mtoNo;           // FMTONO 计划跟踪号（补料单分录FMTONO须与此一致，否则Save校验失败）
    }

    /**
     * 用料清单分录详情（View PRD_PPBOM，上查关联/工序/行号）
     */
    @Data
    public static class PpbomEntryDetail {
        private Long ppbomId;          // 表头FID
        private String ppbomBillNo;
        private String moBillNo;
        private Long entryId;          // 分录内码（FPPBomEntryId/FEntrySrcEnteryId）
        private Integer entrySeq;      // 分录行号（FEntrySrcEntrySeq）
        private String materialNumber;
        private Long operId;           // 工序内码（FOperId）
    }

    /**
     * 即时库存批次行（STK_Inventory）
     */
    @Data
    public static class StockRow {
        private String materialCode;
        private String stockOrgNumber;  // FStockOrgId.FNumber
        private String lotNumber;       // FLot.FNumber 批次
        private String stockNumber;     // FStockId.FNumber 仓库编码
        private String stockName;       // FStockId.FName 仓库名称
        private Long locationId;        // FSTOCKLOCID 仓位值组合内码（未启用仓位管理时为0/null）
        private BigDecimal baseQty;     // FBaseQty 基本单位数量
    }

    /**
     * 生产领料单发料记录行（PRD_PickMtrl，已审核）
     * <p>退料单库存组织/仓库/仓位必须按领料来源还原（从哪里领料、退回哪里），
     * 不得使用即时库存所在组织或默认组织。</p>
     */
    @Data
    public static class PickStockRow {
        private String billNo;            // FBillNo 生产领料单号(SOUT...)
        private String date;              // FDate 领料日期
        private String stockOrgNumber;    // FStockOrgId.FNumber 实际发料库存组织
        private String materialCode;      // FMaterialId.FNumber
        private String stockNumber;       // FStockId.FNumber 实际发料仓库
        private Long stockLocId;          // FStockLocId 仓位值组合内码(0/null=未启用仓位)
        private String lotNumber;         // FLot.FNumber 发料批次
        private Long moEntryId;           // FMOEntryId 生产订单分录内码
        private String moBillNo;          // FMOBillNo
        private String ownerNumber;       // FOwnerId.FNumber 货主
        private String keeperNumber;      // FKeeperId.FNumber 保管者
        private BigDecimal baseActualQty; // FBaseActualQty 实际发料基本单位数量
    }

    /**
     * 生产订单明细信息（View PRD_MO，用于退料单 FMOEntryId/FMOEntrySeq/车间等）
     */
    @Data
    public static class MoEntryDetail {
        private Long moId;              // 单据FID
        private String prdOrgNumber;    // 生产组织
        private Long entryId;           // TreeEntity[0].Id
        private Integer entrySeq;       // TreeEntity[0].Seq
        private String productMaterialNumber; // 产品物料编码
        private String workshopNumber;  // 车间编码
    }

    /**
     * 生产退料单生成结果
     */
    @Data
    public static class ReturnOrderResult {
        private String billNo;
        private Long id;
    }

    /**
     * 退料单分录参数（对应已验证的 PRD_ReturnMtrl FEntity 结构）
     */
    @Data
    public static class ReturnOrderEntry {
        private String materialNumber;
        private String unitNumber;
        private BigDecimal qty;
        private String returnType;      // 退料类型, 字典reasonType=1的ID(即金蝶FReturnType枚举值: 1良品退料 2来料不良退料), Save必需
        private String returnReasonCode; // 金蝶退料原因编码(FReturnReason.FNumber, 如TLYY01_SYS良品退料/TLYY02_SYS来料不良), Save必需
        private String stockNumber;
        private String lotNumber;
        private String moBillNo;
        private Long moId;
        private Long moEntryId;
        private Integer moEntrySeq;
        private String productMaterialNumber;
        private String workshopNumber;
        private Long ppbomId;           // 用料清单表头FID（上查关联）
        private String ppbomBillNo;
        private Long ppbomEntryId;      // 用料清单分录内码
        private Integer ppbomEntrySeq;
        private Long locationId;        // 仓位值组合内码（启用仓位管理的仓库必传，FStockLocId）
        private String mtoNo;           // 计划跟踪号（退料单保存键名FMtoNo，取用料清单FMTONO，不一致金蝶Save拦截）
        private String entrtyMemo;      // 物料行备注（FEntrtyMemo，质检审核时填写，空值传空串）
    }

    /**
     * 补料单分录参数（对应已验证的 PRD_FeedMtrl FEntity 结构）
     */
    @Data
    public static class FeedOrderEntry {
        private String materialNumber;
        private String unitNumber;
        private BigDecimal qty;
        private String stockNumber;
        private String lotNumber;
        private String moBillNo;
        private Long moId;
        private Long moEntryId;
        private Integer moEntrySeq;
        private String productMaterialNumber;
        private String workshopNumber;  // 分录车间（FEntryWorkShopId）
        private Long ppbomId;           // 用料清单表头FID（FEntrySrcInterId/Link）
        private String ppbomBillNo;
        private Long ppbomEntryId;      // 用料清单分录内码（FPPBomEntryId/FEntrySrcEnteryId）
        private Integer ppbomEntrySeq;
        private Long operId;            // 工序内码（FOperId，可空）
        private Long locationId;        // 仓位值组合内码（启用仓位管理的仓库必传，FStockLocId）
        private String mtoNo;           // 计划跟踪号（保存键名FMTONO，取用料清单FMTONO，不一致金蝶Save拦截）
    }

    // ==================================================================
    // 会话管理
    // ==================================================================

    /**
     * 获取（缓存的）登录会话，过期或未登录时重新登录
     */
    public LoginResult getSession() {
        LoginResult login = cachedLogin;
        if (login == null || System.currentTimeMillis() - loginTime > SESSION_TTL_MS) {
            synchronized (this) {
                if (cachedLogin == null || System.currentTimeMillis() - loginTime > SESSION_TTL_MS) {
                    cachedLogin = doLogin();
                    loginTime = System.currentTimeMillis();
                }
                login = cachedLogin;
            }
        }
        return login;
    }

    /** 失效缓存会话（下次调用重新登录） */
    private void invalidateSession() {
        cachedLogin = null;
        loginTime = 0L;
    }

    /**
     * 登录金蝶，返回 sessionId 信息
     */
    public LoginResult login() {
        return doLogin();
    }

    private LoginResult doLogin() {
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
     * 带会话的 POST 调用；HTTP 异常时失效会话并重试一次
     */
    private String postWithSession(String path, Object body) {
        String url = kingdeeProperties.getBaseUrl() + path;
        try {
            return doExchange(url, body);
        } catch (KingdeeApiException e) {
            // 业务错误：直接抛出，无需重试
            throw e;
        } catch (Exception e) {
            log.warn("[Kingdee] 调用失败({})，失效会话后重试一次: {}", path, e.getMessage());
            invalidateSession();
            return doExchange(url, body);
        }
    }

    private String doExchange(String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, getSession().toCookieHeader());

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("金蝶调用失败：HTTP " + response.getStatusCode());
        }
        return response.getBody();
    }

    // ==================================================================
    // 原有：备料明细同步（PRD_PPBOM）
    // ==================================================================

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

        JsonNode rowsNode = extractRows(root, responseBody);

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

    // ==================================================================
    // 补退料：通用查询与单据操作
    // ==================================================================

    /**
     * 通用 ExecuteBillQuery；返回行数组
     */
    private List<JsonNode> executeBillQuery(String formId, String fieldKeys, String filterString,
                                            String orderString, int limit) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("FormId", formId);
        parameters.put("FieldKeys", fieldKeys);
        parameters.put("FilterString", filterString);
        parameters.put("OrderString", orderString == null ? "" : orderString);
        parameters.put("TopRowCount", 0);
        parameters.put("StartRow", 0);
        parameters.put("Limit", limit);

        Map<String, Object> body = new HashMap<>();
        body.put("format", "1");
        body.put("useragent", "ApiClient");
        body.put("rid", "356831840");
        body.put("parameters", Collections.singletonList(parameters));
        body.put("timestamp", "2022-01-04 13:30:213");
        body.put("v", "1.0");

        String responseBody = postWithSession(BILL_QUERY_PATH, body);
        log.info("[Kingdee] ExecuteBillQuery({}) 响应: {}", formId, truncate(responseBody));

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new KingdeeApiException("解析金蝶查询响应失败: " + e.getMessage());
        }
        JsonNode rows = extractRows(root, responseBody);
        List<JsonNode> list = new ArrayList<>();
        rows.forEach(list::add);
        return list;
    }

    /** 构造 'a','b','c' 形式的SQL IN列表 */
    private String inList(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) sb.append(',');
            sb.append('\'').append(v.replace("'", "''")).append('\'');
        }
        return sb.toString();
    }

    /**
     * 接口1：按单号查询生产订单（已审核 FDocumentStatus='C'）
     */
    public MoInfo queryMoByBillNo(String orderCode) {
        List<JsonNode> rows = executeBillQuery("PRD_MO",
                "FBillNo,FDate,FPlanFinishDate,FRptFinishQty,FQty,FStatus,FMaterialId.FNumber,FMaterialId.FName,FPrdOrgId.FNumber,FID",
                "FBillNo='" + orderCode.replace("'", "''") + "' and FDocumentStatus='C'",
                "", 5);
        if (rows.isEmpty()) return null;
        JsonNode row = rows.get(0);
        MoInfo info = new MoInfo();
        info.setBillNo(textOf(row.get(0)));
        info.setDate(textOf(row.get(1)));
        info.setPlanFinishDate(textOf(row.get(2)));
        info.setRptFinishQty(decimalOf(row.get(3)));
        info.setQty(decimalOf(row.get(4)));
        info.setStatus(textOf(row.get(5)));
        info.setProductCode(textOf(row.get(6)));
        info.setProductName(textOf(row.get(7)));
        info.setPrdOrgNumber(textOf(row.get(8)));
        info.setId(longOf(row.get(9)));
        return info;
    }

    /**
     * 接口2：查询订单用料清单（应发/已领）；支持多订单
     * <p>返回含 FID/FBillNo（补/退料单上查关联用料清单所需）。</p>
     */
    public List<PpbomRow> queryPpbom(List<String> orderCodes) {
        List<PpbomRow> result = new ArrayList<>();
        if (orderCodes == null || orderCodes.isEmpty()) return result;
        List<JsonNode> rows = executeBillQuery("PRD_PPBOM",
                "FId,FBillNo,FMOBillNO,FMaterialID2.fnumber,FMaterialID2.fname,FMaterialModel1,FNeedQty2,FUnitID2.fnumber,FUnitID2.fname,FPickedQty,FMTONO",
                "FMOBillNO in (" + inList(orderCodes) + ") and FPickedQty>0", "", 1000);
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 11) continue;
            PpbomRow r = new PpbomRow();
            r.setPpbomId(longOf(row.get(0)));
            r.setPpbomBillNo(textOf(row.get(1)));
            r.setMoBillNo(textOf(row.get(2)));
            r.setMaterialCode(textOf(row.get(3)));
            r.setMaterialName(textOf(row.get(4)));
            r.setModel(textOf(row.get(5)));
            r.setNeedQty(decimalOf(row.get(6)));
            r.setUnitNumber(textOf(row.get(7)));
            r.setUnitName(textOf(row.get(8)));
            r.setPickedQty(decimalOf(row.get(9)));
            r.setMtoNo(textOf(row.get(10)));
            result.add(r);
        }
        return result;
    }

    /**
     * M3：查询物料即时库存批次（FBaseQty>0，按 FUpdateTime 升序 = FIFO）；支持多物料
     * <p>FSTOCKLOCID 为仓位值组合内码（启用仓位管理的仓库必传给单据的 FStockLocId）。</p>
     */
    public List<StockRow> queryStock(List<String> materialCodes) {
        List<StockRow> result = new ArrayList<>();
        if (materialCodes == null || materialCodes.isEmpty()) return result;
        List<JsonNode> rows = executeBillQuery("STK_Inventory",
                "FMaterialId.FNumber,FStockOrgId.FNumber,FLot.FNumber,FStockId.FNumber,FStockId.FName,FSTOCKLOCID,FBaseQty",
                "FMaterialId.FNumber in (" + inList(materialCodes) + ") ",
                "FUpdateTime asc", 2000);
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 7) continue;
            StockRow r = new StockRow();
            r.setMaterialCode(textOf(row.get(0)));
            r.setStockOrgNumber(textOf(row.get(1)));
            r.setLotNumber(textOf(row.get(2)));
            r.setStockNumber(textOf(row.get(3)));
            r.setStockName(textOf(row.get(4)));
            r.setLocationId(longOf(row.get(5)));
            r.setBaseQty(decimalOf(row.get(6)));
            result.add(r);
        }
        return result;
    }

    /**
     * M6：View PRD_MO 获取退料单所需分录信息（TreeEntity首行：entryId/seq/产品/车间）
     */
    public MoEntryDetail viewMoEntry(String orderCode) {
        Map<String, Object> body = baseRequestBody();
        Map<String, Object> numberParam = new HashMap<>();
        numberParam.put("Number", orderCode);
        body.put("parameters", Arrays.asList("PRD_MO", numberParam));

        String responseBody = postWithSession(VIEW_PATH, body);
        log.info("[Kingdee] View(PRD_MO) 响应: {}", truncate(responseBody, 2000));

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.path("Result").path("Result");
            if (result.isMissingNode() || result.isNull()) {
                throw new KingdeeApiException("加载生产订单失败：响应结构异常 " + truncate(responseBody));
            }
            MoEntryDetail detail = new MoEntryDetail();
            detail.setMoId(longOf(result.path("Id")));
            detail.setPrdOrgNumber(result.path("PrdOrgId").path("Number").asText(null));
            JsonNode tree = result.path("TreeEntity");
            if (tree.isArray() && tree.size() > 0) {
                JsonNode first = tree.get(0);
                detail.setEntryId(longOf(first.path("Id")));
                detail.setEntrySeq(first.path("Seq").isNumber() ? first.path("Seq").asInt() : null);
                detail.setProductMaterialNumber(first.path("MaterialId").path("Number").asText(null));
                detail.setWorkshopNumber(first.path("WorkShopID").path("Number").asText(null));
            }
            return detail;
        } catch (KingdeeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new KingdeeApiException("解析生产订单View响应失败: " + e.getMessage());
        }
    }

    /**
     * M6：View PRD_PPBOM 获取用料清单分录详情（分录内码/行号/工序），上查关联所需
     * <p>BillQuery 无法输出分录主键，须通过 View 的 PPBomEntry 集合获取。</p>
     */
    public List<PpbomEntryDetail> viewPpbom(String ppbomBillNo) {
        Map<String, Object> body = baseRequestBody();
        Map<String, Object> numberParam = new HashMap<>();
        numberParam.put("Number", ppbomBillNo);
        body.put("parameters", Arrays.asList("PRD_PPBOM", numberParam));

        String responseBody = postWithSession(VIEW_PATH, body);
        log.info("[Kingdee] View(PRD_PPBOM,{}) 响应: {}", ppbomBillNo, truncate(responseBody, 2000));

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.path("Result").path("Result");
            if (result.isMissingNode() || result.isNull()) {
                throw new KingdeeApiException("加载用料清单失败：响应结构异常 " + truncate(responseBody));
            }
            long ppbomId = longOf(result.path("Id"));
            String billNo = result.path("BillNo").asText(null);
            // 金蝶实际键名为 MOBillNO（全大写MO+NO），与BillQuery的FMOBillNO对应
            String moBillNo = result.path("MOBillNO").asText(null);

            List<PpbomEntryDetail> details = new ArrayList<>();
            JsonNode entries = result.path("PPBomEntry");
            if (entries.isArray()) {
                for (JsonNode e : entries) {
                    PpbomEntryDetail d = new PpbomEntryDetail();
                    d.setPpbomId(ppbomId);
                    d.setPpbomBillNo(billNo);
                    d.setMoBillNo(moBillNo);
                    d.setEntryId(longOf(e.path("Id")));
                    d.setEntrySeq(e.path("Seq").isNumber() ? e.path("Seq").asInt() : null);
                    d.setMaterialNumber(e.path("MaterialID").path("Number").asText(null));
                    // 金蝶实际键名为 OperID（大写D），值为工序内码
                    d.setOperId(longOf(e.path("OperID")));
                    details.add(d);
                }
            }
            return details;
        } catch (KingdeeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new KingdeeApiException("解析用料清单View响应失败: " + e.getMessage());
        }
    }

    /**
     * M6：View 单据审核状态（DocumentStatus：A创建 B审核中 C已审核 D重新审核），
     * 用于重试时判断已生成单据是否已完成，避免重复建单。查询失败返回null。
     */
    public String viewBillDocumentStatus(String formId, String billNo) {
        try {
            Map<String, Object> body = baseRequestBody();
            Map<String, Object> numberParam = new HashMap<>();
            numberParam.put("Number", billNo);
            body.put("parameters", Arrays.asList(formId, numberParam));
            String responseBody = postWithSession(VIEW_PATH, body);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.path("Result").path("Result");
            if (result.isMissingNode() || result.isNull()) return null;
            String status = result.path("DocumentStatus").asText(null);
            log.info("[Kingdee] View({},{}) DocumentStatus={}", formId, billNo, status);
            return status;
        } catch (Exception e) {
            log.warn("[Kingdee] View({},{}) 状态查询失败: {}", formId, billNo, e.getMessage());
            return null;
        }
    }

    /**
     * M6：按生产订单分录+物料查询已审核生产领料单(PRD_PickMtrl)的实际发料记录，
     * 用于退料单库存组织/仓库/仓位按领料来源还原（从哪里领料、退回哪里）。
     * <p>字段键已在租户环境实测验证；按 FDate 降序返回（同物料多次领料时调用方优先取批次匹配行，否则取最近一次）。</p>
     */
    public List<PickStockRow> queryPickStock(Collection<Long> moEntryIds, Collection<String> materialCodes) {
        List<PickStockRow> result = new ArrayList<>();
        if (moEntryIds == null || moEntryIds.isEmpty() || materialCodes == null || materialCodes.isEmpty()) {
            return result;
        }
        List<String> ids = moEntryIds.stream().filter(Objects::nonNull).map(String::valueOf).distinct()
                .collect(java.util.stream.Collectors.toList());
        List<String> codes = materialCodes.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().collect(java.util.stream.Collectors.toList());
        if (ids.isEmpty() || codes.isEmpty()) return result;

        String filter = "FDocumentStatus='C' and FMOEntryId in (" + String.join(",", ids)
                + ") and FMaterialId.FNumber in (" + inList(codes) + ")";
        List<JsonNode> rows = executeBillQuery("PRD_PickMtrl",
                "FBillNo,FDate,FStockOrgId.FNumber,FMaterialId.FNumber,FStockId.FNumber,FStockLocId,"
                        + "FLot.FNumber,FMOEntryId,FMOBillNo,FOwnerId.FNumber,FKeeperId.FNumber,FBaseActualQty",
                filter, "FDate desc", 2000);
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 12) continue;
            PickStockRow r = new PickStockRow();
            r.setBillNo(textOf(row.get(0)));
            r.setDate(textOf(row.get(1)));
            r.setStockOrgNumber(textOf(row.get(2)));
            r.setMaterialCode(textOf(row.get(3)));
            r.setStockNumber(textOf(row.get(4)));
            r.setStockLocId(longOf(row.get(5)));
            r.setLotNumber(textOf(row.get(6)));
            r.setMoEntryId(longOf(row.get(7)));
            r.setMoBillNo(textOf(row.get(8)));
            r.setOwnerNumber(textOf(row.get(9)));
            r.setKeeperNumber(textOf(row.get(10)));
            r.setBaseActualQty(decimalOf(row.get(11)));
            result.add(r);
        }
        return result;
    }

    /**
     * M6：View 单据表头库存组织编码（FStockOrgId.FNumber）。
     * 用于重试续传时判断已保存退料单的库存组织是否与领料来源一致：组织不一致的旧草稿不得续传，需重新建单。
     * 查询失败返回null（由调用方按续传策略处理）。
     */
    public String viewBillStockOrg(String formId, String billNo) {
        try {
            Map<String, Object> body = baseRequestBody();
            Map<String, Object> numberParam = new HashMap<>();
            numberParam.put("Number", billNo);
            body.put("parameters", Arrays.asList(formId, numberParam));
            String responseBody = postWithSession(VIEW_PATH, body);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.path("Result").path("Result");
            if (result.isMissingNode() || result.isNull()) return null;
            String org = result.path("StockOrgId").path("Number").asText(null);
            log.info("[Kingdee] View({},{}) StockOrgId.FNumber={}", formId, billNo, org);
            return org;
        } catch (Exception e) {
            log.warn("[Kingdee] View({},{}) 库存组织查询失败: {}", formId, billNo, e.getMessage());
            return null;
        }
    }

    /**
     * M6：生成生产退料单（Save → Submit → Audit），返回单号
     * <p>分录通过 FSrcBillType/FPPBomEntryId/FPPBomBillNo/FEntity_Link 建立与用料清单(PRD_PPBOM)的关联，
     * 保证金蝶中可上查到生产用料清单。</p>
     * <p>调用前必须确保各分录的 moEntry/ppbom 信息已通过 {@link #viewMoEntry} / {@link #queryPpbom} / {@link #viewPpbom} 获取。</p>
     * @param resumeBillNo 续传单号：之前Save成功但Submit/Audit失败的单号，非空时跳过Save直接续传
     */
    public ReturnOrderResult createReturnOrder(String date, String stockOrgNumber, String prdOrgNumber,
                                                String description, List<ReturnOrderEntry> entries,
                                                String resumeBillNo) {
        String billNo = (resumeBillNo == null || resumeBillNo.isEmpty()) ? null : resumeBillNo;

        // 1. Save（已有续传单号时跳过，避免重复建单）
        if (billNo == null) {
            List<Map<String, Object>> entityList = new ArrayList<>();
            for (ReturnOrderEntry e : entries) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("FMaterialId", Collections.singletonMap("FNumber", e.getMaterialNumber()));
                entry.put("FUnitID", Collections.singletonMap("FNumber", e.getUnitNumber()));
                entry.put("FAPPQty", e.getQty());
                entry.put("FQty", e.getQty());
                // 退料类型/退料原因：金蝶保存生产退料单必需字段，缺失将导致Save失败
                if (e.getReturnType() != null) {
                    entry.put("FReturnType", e.getReturnType());
                }
                if (e.getReturnReasonCode() != null && !e.getReturnReasonCode().isEmpty()) {
                    entry.put("FReturnReason", Collections.singletonMap("FNumber", e.getReturnReasonCode()));
                }
                entry.put("FStockId", Collections.singletonMap("FNumber", e.getStockNumber()));
                entry.put("FLot", Collections.singletonMap("FNumber", e.getLotNumber()));
                entry.put("FStockStatusId", Collections.singletonMap("FNumber", "KCZT01_SYS"));
                entry.put("FOwnerTypeId", "BD_OwnerOrg");
                entry.put("FOwnerId", Collections.singletonMap("FNumber", stockOrgNumber));
                entry.put("FKeeperTypeId", "BD_KeeperOrg");
                entry.put("FKeeperId", Collections.singletonMap("FNumber", stockOrgNumber));
                entry.put("FMOBillNo", e.getMoBillNo());
                // 计划跟踪号（方案A：保存前对齐用料清单FMTONO）：退料单保存键名为FMtoNo（与补料单FMTONO大小写不同），
                // 原样填入用料清单分录跟踪号；用料清单分录为空跟踪号时也必须传空串，不能省略/填null或默认值，
                // 否则金蝶仍判"分录计划跟踪号与用料清单不一致"
                entry.put("FMtoNo", e.getMtoNo() == null ? "" : e.getMtoNo().trim());
                entry.put("FMOId", e.getMoId());
                entry.put("FMOEntryId", e.getMoEntryId());
                entry.put("FMOEntrySeq", e.getMoEntrySeq());
                // 用料清单关联（上查）：源单类型/编号 + 分录内码 + 转换规则Link
                entry.put("FSrcBillType", "PRD_PPBOM");
                entry.put("FSrcBillNo", e.getPpbomBillNo());
                entry.put("FPPBomEntryId", e.getPpbomEntryId());
                //entry.put("FIsUpdateQty", true);
                entry.put("FPPBomBillNo", e.getPpbomBillNo());
                if (e.getPpbomId() != null && e.getPpbomEntryId() != null) {
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("FEntity_Link_FRuleId", "PRD_PPBOM2RETURNMTRL");
                    link.put("FEntity_Link_FSBillId", e.getPpbomId());
                    link.put("FEntity_Link_FSId", e.getPpbomEntryId());
                    link.put("FEntity_Link_FSTableName", "T_PRD_PPBOMENTRY");
                    //link.put("FEntity_Link_FBaseQtyOld", e.getQty());
                    link.put("FEntity_Link_FBaseQty", e.getQty());
                    entry.put("FEntity_Link", Collections.singletonList(link));
                }
                entry.put("FParentOwnerId", Collections.singletonMap("FNumber", stockOrgNumber));
                entry.put("FParentMaterialId", Collections.singletonMap("FNumber", e.getProductMaterialNumber()));
                entry.put("FWorkShopId1", Collections.singletonMap("FNumber", e.getWorkshopNumber()));
                // 仓位（启用仓位管理的仓库必录）：传仓位值组合内码（裸数字）
                if (e.getLocationId() != null && e.getLocationId() > 0) {
                    entry.put("FStockLocId", e.getLocationId());
                }
                // 物料行备注：金蝶生产退料单分录 FEntrtyMemo，保证字符串（空值传空串）
                entry.put("FEntrtyMemo", e.getEntrtyMemo() == null ? "" : e.getEntrtyMemo());
                entityList.add(entry);
            }

            Map<String, Object> model = new LinkedHashMap<>();
            model.put("FBillType", Collections.singletonMap("FNumber", "SCTLD01_SYS"));
            model.put("FDate", date);
            model.put("FStockOrgId", Collections.singletonMap("FNumber", stockOrgNumber));
            model.put("FPrdOrgId", Collections.singletonMap("FNumber", prdOrgNumber));
            model.put("FOwnerTypeId", "BD_OwnerOrg");
            model.put("FOwnerId", Collections.singletonMap("FNumber", stockOrgNumber));
            model.put("FDescription", description);
            model.put("FEntity", entityList);

            Map<String, Object> packet = new LinkedHashMap<>();
            packet.put("NeedUpDateFields", Collections.emptyList());
            packet.put("NeedReturnFields", Collections.singletonList("FBillNo"));
            packet.put("IsDeleteEntry", true);
            packet.put("Model", model);

            // 方案B：Save交互警告（计划跟踪号与用料清单不一致）由saveBillWithInteraction识别并重放
            JsonNode saveResult = saveBillWithInteraction("PRD_ReturnMtrl", packet, "退料单保存");
            billNo = saveResult.path("Number").asText(null);
            if (billNo == null || billNo.isEmpty()) {
                throw new KingdeeApiException("退料单保存成功但未返回单号");
            }
        }

        Long billId = null;

        // 2. Submit（失败时携带已保存单号抛出，供重试续传）
        /***try {
            Map<String, Object> submitBody = baseRequestBody();
            submitBody.put("parameters", Arrays.asList("PRD_ReturnMtrl",
                    Collections.singletonMap("Numbers", Collections.singletonList(billNo))));
            String submitResp = postWithSession(SUBMIT_PATH, submitBody);
            log.info("[Kingdee] Submit(PRD_ReturnMtrl,{}) 响应: {}", billNo, truncate(submitResp));
            parseOperationResult(submitResp, "退料单提交");
        } catch (KingdeeApiException e) {
            if (e instanceof BillStageException) throw e;
            throw new BillStageException("PRD_ReturnMtrl", billNo, e.getMessage());
        }

        // 3. Audit
        try {
            Map<String, Object> auditBody = baseRequestBody();
            auditBody.put("parameters", Arrays.asList("PRD_ReturnMtrl",
                    Collections.singletonMap("Numbers", Collections.singletonList(billNo))));
            String auditResp = postWithSession(AUDIT_PATH, auditBody);
            log.info("[Kingdee] Audit(PRD_ReturnMtrl,{}) 响应: {}", billNo, truncate(auditResp));
            parseOperationResult(auditResp, "退料单审核");
        } catch (KingdeeApiException e) {
            if (e instanceof BillStageException) throw e;
            throw new BillStageException("PRD_ReturnMtrl", billNo, e.getMessage());
        }***/

        ReturnOrderResult result = new ReturnOrderResult();
        result.setBillNo(billNo);
        result.setId(billId);
        return result;
    }

    /**
     * M6：生成生产补料单（PRD_FeedMtrl，单据类型SCBLD01_SYS，Save → Submit → Audit），返回单号
     * <p>分录通过 FEntity_Link（转换规则PRDPPBomTrans2FeedBill，与退料单同机制）+ FEntrySrc*（源单内码/类型/编号/分录/行号）
     * + FPPBomEntryId/FPPBomBillNo + FMo* + FOperId 建立与用料清单(PRD_PPBOM)的关联，保证金蝶中可上查到生产用料清单。</p>
     * @param workshopNumber 表头车间（FWorkShopId）
     * @param stockNumber    表头发料仓库（FStockId0）
     * @param applicantName  补料申请人（分录备注 FEntrtyDescription）
     * @param resumeBillNo   续传单号：之前Save成功但Submit/Audit失败的单号，非空时跳过Save直接续传
     */
    public ReturnOrderResult createFeedOrder(String date, String stockOrgNumber, String prdOrgNumber,
                                             String workshopNumber, String stockNumber, String description,
                                             String applicantName, List<FeedOrderEntry> entries,
                                             String resumeBillNo) {
        String billNo = (resumeBillNo == null || resumeBillNo.isEmpty()) ? null : resumeBillNo;

        // 1. Save（已有续传单号时跳过，避免重复建单）
        if (billNo == null) {
            List<Map<String, Object>> entityList = new ArrayList<>();
            for (FeedOrderEntry e : entries) {
                BigDecimal qty = e.getQty() == null ? BigDecimal.ZERO : e.getQty();
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("FParentMaterialId", Collections.singletonMap("FNumber", e.getProductMaterialNumber()));
                entry.put("FConsome", "0");
                entry.put("FReserveType", "1");
                entry.put("FBaseStockActualQty", qty);
                entry.put("FMaterialId", Collections.singletonMap("FNumber", e.getMaterialNumber()));
                entry.put("FUnitID", Collections.singletonMap("FNumber", e.getUnitNumber()));
                entry.put("FAppQty", qty);
                entry.put("FActualQty", qty);
                entry.put("FEntryVmiBusiness", false);
                entry.put("FScrapQty", 0.0);
                entry.put("FOptQueue", "0");
                entry.put("FStockId", Collections.singletonMap("FNumber", e.getStockNumber()));
                // 仓位（启用仓位管理的仓库必录）：传仓位值组合内码（裸数字）
                if (e.getLocationId() != null && e.getLocationId() > 0) {
                    entry.put("FStockLocId", e.getLocationId());
                }
                entry.put("FOptPlanBillId", 0);
                entry.put("FOptDetailId", 0);
                entry.put("FLot", Collections.singletonMap("FNumber", e.getLotNumber()));
                entry.put("FTransRetId", 0);
                entry.put("FTransRetEntryId", 0);
                entry.put("FTransRetEntrySeq", 0);
                entry.put("FFeedReasonId", Collections.singletonMap("FNumber", "BLYY01_SYS"));
                entry.put("FIsOverLegalOrg", false);
                entry.put("FCheckReturnMtrl", false);
                entry.put("FEntrtyDescription", "补料申请人：" + applicantName);
                entry.put("FStockStatusId", Collections.singletonMap("FNumber", "KCZT01_SYS"));
                entry.put("FMoBillNo", e.getMoBillNo());
                // 计划跟踪号（方案A）：与用料清单FMTONO原样对齐；为空时也必须传空串，不能省略/填null
                entry.put("FMTONO", e.getMtoNo() == null ? "" : e.getMtoNo().trim());
                entry.put("FMoEntryId", e.getMoEntryId());
                entry.put("FPPBomEntryId", e.getPpbomEntryId());
                if (e.getOperId() != null) {
                    entry.put("FOperId", e.getOperId());
                }
                entry.put("FOwnerTypeId", "BD_OwnerOrg");
                entry.put("FStockAppQty", qty);
                entry.put("FStockActualQty", qty);
                entry.put("FSecActualQty", 0.0);
                entry.put("FMoId", e.getMoId());
                entry.put("FMoEntrySeq", e.getMoEntrySeq());
                entry.put("FBaseAppQty", qty);
                entry.put("FStockScrapQty", 0.0);
                entry.put("FSecScrapQty", 0.0);
                entry.put("FBaseScrapQty", 0.0);
                entry.put("FPPBomBillNo", e.getPpbomBillNo());
                entry.put("FBaseUnitId", Collections.singletonMap("FNumber", e.getUnitNumber()));
                entry.put("FStockUnitId", Collections.singletonMap("FNumber", e.getUnitNumber()));
                entry.put("FEntryWorkShopId", Collections.singletonMap("FNumber", e.getWorkshopNumber()));
                entry.put("FBaseActualQty", qty);
                entry.put("FKeeperTypeId", "BD_KeeperOrg");
                entry.put("FKeeperId", Collections.singletonMap("FNumber", stockOrgNumber));
                entry.put("FOwnerId", Collections.singletonMap("FNumber", stockOrgNumber));
                // 用料清单关联（上查）：源单内码/类型/分录/编号/行号
                entry.put("FEntrySrcInterId", e.getPpbomId());
                entry.put("FEntrySrcBillType", "PRD_PPBOM");
                entry.put("FEntrySrcEnteryId", e.getPpbomEntryId());
                entry.put("FEntrySrcBillNo", e.getPpbomBillNo());
                entry.put("FPrice", 0.0);
                entry.put("FAmount", 0.0);
                entry.put("FParentOwnerTypeId", "BD_OwnerOrg");
                entry.put("FParentOwnerId", Collections.singletonMap("FNumber", stockOrgNumber));
                entry.put("FEntrySrcEntrySeq", e.getPpbomEntrySeq());
                entry.put("FSrcBizInterId", 0);
                entry.put("FSrcBizEntryId", 0);
                entry.put("FSrcBizEntrySeq", 0);
                // 用料清单关联（上查）：转换规则Link，与退料单同机制（规则ID为用料清单→补料单），
                // 控制字段用于审核时反写补料数量到用料清单
                if (e.getPpbomId() != null && e.getPpbomEntryId() != null) {
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("FEntity_Link_FRuleId", "PRDPPBomTrans2FeedBill");
                    link.put("FEntity_Link_FSTableName", "T_PRD_PPBOMENTRY");
                    link.put("FEntity_Link_FSBillId", e.getPpbomId());
                    link.put("FEntity_Link_FSId", e.getPpbomEntryId());
                    link.put("FEntity_Link_FBaseActualQtyOld", qty);
                    link.put("FEntity_Link_FBaseActualQty", qty);
                    link.put("FEntity_Link_FBaseScrapQtyOld", 0.0);
                    link.put("FEntity_Link_FBaseScrapQty", 0.0);
                    entry.put("FEntity_Link", Collections.singletonList(link));
                }
                entityList.add(entry);
            }

            Map<String, Object> model = new LinkedHashMap<>();
            model.put("FID", 0);
            model.put("FBillType", Collections.singletonMap("FNumber", "SCBLD01_SYS"));
            model.put("FDate", date);
            model.put("FStockOrgId", Collections.singletonMap("FNumber", stockOrgNumber));
            model.put("FStockId0", Collections.singletonMap("FNumber", stockNumber));
            model.put("FPrdOrgId", Collections.singletonMap("FNumber", prdOrgNumber));
            model.put("FWorkShopId", Collections.singletonMap("FNumber", workshopNumber));
            model.put("FOwnerTypeId0", "BD_OwnerOrg");
            model.put("FCurrId", Collections.singletonMap("FNumber", "PRE001"));
            model.put("FIsCrossTrade", false);
            model.put("FVmiBusiness", false);
            model.put("FIsOwnerTInclOrg", false);
            model.put("FDescription", description);
            model.put("FEntity", entityList);

            Map<String, Object> packet = new LinkedHashMap<>();
            packet.put("NeedUpDateFields", Collections.emptyList());
            packet.put("NeedReturnFields", Collections.emptyList());
            packet.put("IsDeleteEntry", "true");
            packet.put("SubSystemId", "");
            packet.put("IsVerifyBaseDataField", "false");
            packet.put("IsEntryBatchFill", "true");
            packet.put("ValidateFlag", "true");
            packet.put("NumberSearch", "true");
            packet.put("IsAutoAdjustField", "true");
            packet.put("InterationFlags", "");
            packet.put("IgnoreInterationFlag", "");
            packet.put("IsControlPrecision", "false");
            packet.put("ValidateRepeatJson", "true");
            packet.put("Model", model);

            String packetJson;
            try {
                packetJson = objectMapper.writeValueAsString(packet);
            } catch (Exception ex) {
                throw new KingdeeApiException("序列化补料单报文失败: " + ex.getMessage());
            }

            Map<String, Object> saveBody = baseRequestBody();
            saveBody.put("parameters", Arrays.asList("PRD_FeedMtrl", packetJson));
            String saveResp = postWithSession(SAVE_PATH, saveBody);
            log.info("[Kingdee] Save(PRD_FeedMtrl) 响应: {}", truncate(saveResp, 2000));

            JsonNode saveResult = parseOperationResult(saveResp, "补料单保存");
            billNo = saveResult.path("Number").asText(null);
            if (billNo == null || billNo.isEmpty()) {
                throw new KingdeeApiException("补料单保存成功但未返回单号: " + truncate(saveResp));
            }
        }

        // 2. Submit（失败时携带已保存单号抛出，供重试续传）
        /***try {
            Map<String, Object> submitBody = baseRequestBody();
            submitBody.put("parameters", Arrays.asList("PRD_FeedMtrl",
                    Collections.singletonMap("Numbers", Collections.singletonList(billNo))));
            String submitResp = postWithSession(SUBMIT_PATH, submitBody);
            log.info("[Kingdee] Submit(PRD_FeedMtrl,{}) 响应: {}", billNo, truncate(submitResp));
            parseOperationResult(submitResp, "补料单提交");
        } catch (KingdeeApiException e) {
            if (e instanceof BillStageException) throw e;
            throw new BillStageException("PRD_FeedMtrl", billNo, e.getMessage());
        }

        // 3. Audit
        try {
            Map<String, Object> auditBody = baseRequestBody();
            auditBody.put("parameters", Arrays.asList("PRD_FeedMtrl",
                    Collections.singletonMap("Numbers", Collections.singletonList(billNo))));
            String auditResp = postWithSession(AUDIT_PATH, auditBody);
            log.info("[Kingdee] Audit(PRD_FeedMtrl,{}) 响应: {}", billNo, truncate(auditResp));
            parseOperationResult(auditResp, "补料单审核");
        } catch (KingdeeApiException e) {
            if (e instanceof BillStageException) throw e;
            throw new BillStageException("PRD_FeedMtrl", billNo, e.getMessage());
        }***/

        ReturnOrderResult result = new ReturnOrderResult();
        result.setBillNo(billNo);
        return result;
    }

    /**
     * 方案B：Save 调用并处理"计划跟踪号与用料清单不一致"等需交互确认的警告。
     * <p>金蝶 WebAPI 无界面可点"是"：检测到该交互警告时，从当次响应中提取交互标识
     * (MsgId/InterationFlag，不同补丁版本字段可能不同)，用同一数据包在 packet.InterationFlags
     * 中带回标识重新调用保存（标识分号分隔），等同于用户点击"是"。</p>
     * <p>标识不写死：仅从当次响应提取；响应未回传标识时使用配置 kingdee.mto-interaction-flag 兜底，
     * 仍无标识则按普通失败抛出。最多重放2轮，避免死循环。</p>
     */
    private JsonNode saveBillWithInteraction(String formId, Map<String, Object> packet, String operation) {
        packet.putIfAbsent("InterationFlags", "");
        packet.putIfAbsent("IgnoreInterationFlag", "");

        String responseBody = postSaveForm(formId, packet);
        log.info("[Kingdee] Save({}) 响应: {}", formId, truncate(responseBody, 2000));

        Set<String> usedFlags = new LinkedHashSet<>();
        for (int round = 0; round < 2; round++) {
            JsonNode root;
            try {
                root = objectMapper.readTree(responseBody);
            } catch (Exception e) {
                throw new KingdeeApiException("解析" + operation + "响应失败: " + e.getMessage());
            }
            JsonNode status = root.path("Result").path("ResponseStatus");
            if (status.path("IsSuccess").asBoolean(false)) {
                return root.path("Result");
            }
            // 非计划跟踪号类交互警告，不做重放，走标准错误解析
            if (!containsMtoInteractionHint(status)) {
                break;
            }
            List<String> flags = extractInteractionFlags(status);
            if (flags.isEmpty()) {
                String fallback = kingdeeProperties.getMtoInteractionFlag();
                if (fallback != null && !fallback.trim().isEmpty()) {
                    flags = Arrays.stream(fallback.split(";"))
                            .map(String::trim).filter(s -> !s.isEmpty())
                            .collect(java.util.stream.Collectors.toList());
                }
            }
            flags.removeIf(usedFlags::contains);
            if (flags.isEmpty()) {
                log.warn("[Kingdee] {}(formId={}) 检测到计划跟踪号交互警告，但响应未回传交互标识且未配置兜底标识，无法自动重放",
                        operation, formId);
                break;
            }
            usedFlags.addAll(flags);
            packet.put("InterationFlags", String.join(";", usedFlags));
            log.info("[Kingdee] {}(formId={}) 检测到交互警告，携带 InterationFlags={} 重放保存(第{}轮)",
                    operation, formId, usedFlags, round + 1);
            responseBody = postSaveForm(formId, packet);
            log.info("[Kingdee] Save({}) 重放响应: {}", formId, truncate(responseBody, 2000));
        }
        return parseOperationResult(responseBody, operation);
    }

    /** 序列化packet并调用Save */
    private String postSaveForm(String formId, Map<String, Object> packet) {
        String packetJson;
        try {
            packetJson = objectMapper.writeValueAsString(packet);
        } catch (Exception e) {
            throw new KingdeeApiException("序列化" + formId + "保存报文失败: " + e.getMessage());
        }
        Map<String, Object> saveBody = baseRequestBody();
        saveBody.put("parameters", Arrays.asList(formId, packetJson));
        return postWithSession(SAVE_PATH, saveBody);
    }

    /** 判断响应状态中是否包含"计划跟踪号与用料清单不一致/是否继续保存"交互警告文案 */
    private boolean containsMtoInteractionHint(JsonNode status) {
        if (status == null || status.isMissingNode()) return false;
        String joined = collectMessageTexts(status).toString();
        return joined.contains("计划跟踪号") && (joined.contains("用料清单") || joined.contains("不一致"));
    }

    /** 递归收集 Errors/Warnings/SuccessMessages 中的 Message 文本 */
    private StringBuilder collectMessageTexts(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        collectMessageTexts(node, sb);
        return sb;
    }

    private void collectMessageTexts(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            JsonNode msg = node.get("Message");
            if (msg != null && msg.isTextual()) sb.append(msg.asText()).append(';');
            node.fields().forEachRemaining(en -> collectMessageTexts(en.getValue(), sb));
        } else if (node.isArray()) {
            node.forEach(n -> collectMessageTexts(n, sb));
        }
    }

    /**
     * 从响应状态节点中提取交互标识：递归查找键名含 interation/interaction 或 MsgId 的非空标量值。
     * 金蝶不同补丁版本可能将标识放在 ResponseStatus 或各 Errors/Warnings 条目下。
     */
    private List<String> extractInteractionFlags(JsonNode node) {
        Set<String> flags = new LinkedHashSet<>();
        collectInteractionFlags(node, flags);
        return new ArrayList<>(flags);
    }

    private void collectInteractionFlags(JsonNode node, Set<String> flags) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(en -> {
                String key = en.getKey();
                JsonNode v = en.getValue();
                if (v != null && v.isValueNode() && !v.isNull()) {
                    String lk = key.toLowerCase();
                    String text = v.asText("").trim();
                    if (!text.isEmpty() && (lk.contains("interation") || lk.contains("interaction")
                            || lk.equals("msgid"))) {
                        // 标识可能分号分隔多个
                        Arrays.stream(text.split(";")).map(String::trim)
                                .filter(s -> !s.isEmpty()).forEach(flags::add);
                    }
                }
                collectInteractionFlags(v, flags);
            });
        } else if (node.isArray()) {
            node.forEach(n -> collectInteractionFlags(n, flags));
        }
    }

    /** 解析 Save/Submit/Audit 操作响应，业务失败时抛出异常 */
    private JsonNode parseOperationResult(String responseBody, String operation) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.path("Result");
            JsonNode status = result.path("ResponseStatus");
            boolean success = status.path("IsSuccess").asBoolean(false);
            if (!success) {
                throw new KingdeeApiException(operation + "失败: " + extractErrors(status, responseBody));
            }
            return result;
        } catch (KingdeeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new KingdeeApiException("解析" + operation + "响应失败: " + e.getMessage());
        }
    }

    /** WebAPI 标准请求骨架 */
    private Map<String, Object> baseRequestBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("format", 1);
        body.put("useragent", "ApiClient");
        body.put("rid", "356831840");
        body.put("timestamp", "2022-01-04 13:30:213");
        body.put("v", "1.0");
        return body;
    }

    // ==================================================================
    // 通用解析工具
    // ==================================================================

    /** 兼容多种响应结构提取行数组，并识别错误响应 */
    private JsonNode extractRows(JsonNode root, String responseBody) {
        // 形式 1: 直接是数组 [[...], [...]]
        if (root.isArray()) return root;
        // 形式 2/3/4
        if (root.has("Result")) {
            JsonNode result = root.get("Result");
            if (result.isArray()) return result;
            if (result.isObject()) {
                JsonNode status = result.path("ResponseStatus");
                if (status.has("Errors") && status.get("Errors").isArray()
                        && status.get("Errors").size() > 0) {
                    throw new KingdeeApiException("金蝶查询返回错误: " + extractErrors(status, responseBody));
                }
            }
        }
        if (root.has("value") && root.get("value").isArray()) return root.get("value");
        throw new KingdeeApiException("金蝶查询响应格式无法识别: " + truncate(responseBody));
    }

    private String extractErrors(JsonNode status, String responseBody) {
        JsonNode errors = status.path("Errors");
        StringBuilder errMsg = new StringBuilder();
        if (errors.isArray()) {
            for (JsonNode err : errors) {
                if (errMsg.length() > 0) errMsg.append("; ");
                errMsg.append(err.has("Message") ? err.get("Message").asText() : err.toString());
            }
        }
        if (errMsg.length() == 0) errMsg.append(truncate(responseBody));
        return errMsg.toString();
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

    private BigDecimal decimalOf(JsonNode node) {
        if (node == null || node.isNull()) return BigDecimal.ZERO;
        try {
            if (node.isNumber()) return node.decimalValue();
            String s = node.asText();
            return (s == null || s.isEmpty()) ? BigDecimal.ZERO : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Long longOf(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            if (node.isNumber()) return node.asLong();
            String s = node.asText();
            return (s == null || s.isEmpty()) ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
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
        return truncate(s, 500);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "...(truncated)" : s;
    }
}
