package com.enterprise.brain.task.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.service.KingdeeService;
import com.enterprise.brain.task.service.WmsService;
import com.enterprise.brain.task.wms.dto.WmsPpbomRowVO;
import com.enterprise.brain.task.wms.dto.WmsPickingSendRequest;
import com.enterprise.brain.task.wms.dto.WmsPickingSendResponse;
import com.enterprise.brain.task.wms.entity.WmsPicking;
import com.enterprise.brain.task.wms.entity.WmsPickingEntry;
import com.enterprise.brain.task.wms.mapper.WmsPickingEntryMapper;
import com.enterprise.brain.task.wms.mapper.WmsPickingMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WMS 叫料业务服务
 *
 * 功能：
 *  1. 按生产订单号查询金蝶 PRD_PPBOM（生产用料清单）子项明细
 *  2. 保存用户挑选的领料单头 + 明细（状态 pending）
 *  3. 组装 WMS ProductionPicking JSON 并发送
 *  4. 依据 Success 更新状态 sent/failed
 */
@Slf4j
@Service
public class WmsPickingService {

    @Resource
    private WmsPickingMapper pickingMapper;

    @Resource
    private WmsPickingEntryMapper entryMapper;

    @Resource
    private KingdeeService kingdeeService;

    @Resource
    private WmsService wmsService;

    @Resource
    private PlatformTransactionManager transactionManager;

    // ======================= 1. 查金蝶 PRD_PPBOM =======================

    /**
     * 按生产订单号（PRD_PPBOM.FMOBillNO）查询子项物料明细
     * FieldKeys 顺序: FBillNO, FMOBillNO, FMaterialID.fnumber, FMaterialID.fname,
     *                 FMaterialID2.fnumber, FMaterialID2.fname, FNeedQty2,
     *                 FUnitID2.fnumber, FUnitID2.fname, FMTONO,
     *                 FPickStockId
     */
    public List<WmsPpbomRowVO> queryPpbomByProductionNo(String productionNo) {
        if (productionNo == null || productionNo.isBlank()) {
            return new ArrayList<>();
        }
        String filter = "FMOBillNO = '" + productionNo.replace("'", "''") + "'";
        String fieldKeys = "FBillNo,FMOBillNO,FMaterialID.fnumber,FMaterialID.fname," +
                "FMaterialID2.fnumber,FMaterialID2.fname,FNeedQty2,FUnitID2.fnumber,FUnitID2.fname," +
                "FMTONO,FPickStockId";

        log.info("[WMS叫料] 查金蝶 PRD_PPBOM filter={}", filter);
        List<JsonNode> rows = kingdeeService.executeBillQuery("PRD_PPBOM", fieldKeys, filter,
                null, 500);

        List<WmsPpbomRowVO> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 11) continue;
            WmsPpbomRowVO vo = new WmsPpbomRowVO();
            vo.setPpbomBillNo(kingdeeService.textOf(row.get(0)));
            vo.setProductionNo(kingdeeService.textOf(row.get(1)));
            vo.setProductCode(kingdeeService.textOf(row.get(2)));
            vo.setProductName(kingdeeService.textOf(row.get(3)));
            vo.setMaterialCode(kingdeeService.textOf(row.get(4)));
            vo.setMaterialName(kingdeeService.textOf(row.get(5)));
            vo.setNeedQty(kingdeeService.decimalOf(row.get(6)));
            vo.setUnitNumber(kingdeeService.textOf(row.get(7)));
            vo.setUnitName(kingdeeService.textOf(row.get(8)));
            vo.setPlanCode(kingdeeService.textOf(row.get(9)));
            vo.setPickStock(kingdeeService.textOf(row.get(10)));
            result.add(vo);
        }
        log.info("[WMS叫料] PRD_PPBOM 返回 {} 条子项明细", result.size());
        return result;
    }

    // ======================= 2. 保存待发送的领料单 =======================

    /**
     * 保存领料单头 + 明细（状态 pending）
     * 同一个 productionNo + lineCode 如果已存在 pending 状态的单子会覆盖
     */
    @Transactional
    public WmsPicking savePicking(WmsPickingSendRequest request) {
        // 先删同一订单+产线的旧 pending 记录（避免重复）
        pickingMapper.selectList(new LambdaQueryWrapper<WmsPicking>()
                .eq(WmsPicking::getProductionNo, request.getProductionNo())
                .eq(WmsPicking::getLineCode, request.getLineCode())
                .eq(WmsPicking::getStatus, "pending"))
                .forEach(old -> {
                    entryMapper.delete(new LambdaQueryWrapper<WmsPickingEntry>()
                            .eq(WmsPickingEntry::getPickingId, old.getId()));
                    pickingMapper.deleteById(old.getId());
                });

        WmsPicking head = new WmsPicking();
        head.setProductionNo(request.getProductionNo());
        head.setLineCode(request.getLineCode());
        head.setOrgCode(request.getOrgCode());
        head.setDeptCode(request.getDeptCode());
        head.setStatus("pending");
        head.setBillNo(generateBillNo());
        head.setCreateTime(LocalDateTime.now());
        head.setUpdateTime(LocalDateTime.now());
        pickingMapper.insert(head);

        int itemNo = 1;
        for (WmsPickingSendRequest.Entry e : request.getEntries()) {
            WmsPickingEntry entry = new WmsPickingEntry();
            entry.setPickingId(head.getId());
            entry.setItemNo(itemNo++);
            entry.setMaterialCode(e.getMaterialCode());
            entry.setMaterialName(e.getMaterialName());
            entry.setPickedQty(e.getQuantity());
            entry.setUnitCode(e.getUnitCode());
            entry.setPlanCode(e.getPlanCode());
            entry.setStationCode(e.getProductionStationCode());
            entry.setWarehouseCode(e.getOutWarehouseCode());
            entry.setCreateTime(LocalDateTime.now());
            entry.setUpdateTime(LocalDateTime.now());
            entryMapper.insert(entry);
        }
        return head;
    }

    // ======================= 3. 推送 WMS + 存本地库（一步到位） =======================

    /**
     * 推送 WMS 领料申请并持久化：先推 WMS，再把结果（sent/failed）存本地库，
     * 前端一次调用即可，同时保留查询历史的能力。
     *
     * 存库用编程式事务强制 commit，确保后续抛 BusinessException 时不会被 @Transactional 回滚。
     */
    public WmsPicking pushToWms(WmsPickingSendRequest request) {
        String billNo = "MES-PK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();

        // 0. 从 entries 拿产品编码（前端查 PRD_PPBOM 时已经带下来了，同一订单相同）
        String productCode = "";
        String productName = "";
        if (request.getEntries() != null && !request.getEntries().isEmpty()) {
            WmsPickingSendRequest.Entry first = request.getEntries().get(0);
            productCode = first.getProductCode() != null ? first.getProductCode() : "";
            productName = first.getProductName() != null ? first.getProductName() : "";
        }

        // 1. 组装 WMS ProductionPicking JSON
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("BillCode", billNo);
        data.put("ProdctionNo", request.getProductionNo());
        data.put("ProductCode", productCode);
        data.put("ProductName", productName);
        data.put("OrderTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "+08:00");
        data.put("OrganizationCode", request.getOrgCode() != null ? request.getOrgCode() : "102");
        data.put("DepartmentCode", request.getDeptCode() != null ? request.getDeptCode() : "1022016");
        data.put("OrderType", 1);
        data.put("DocumentType", "ProductionPicking");
        data.put("CustomerCode", null);
        data.put("CostCenterCode", request.getOrgCode() != null ? request.getOrgCode() : "102");
        data.put("LineCode", request.getLineCode());

        List<Map<String, Object>> wmsEntries = new ArrayList<>();
        int itemNo = 1;
        for (WmsPickingSendRequest.Entry e : request.getEntries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ItemNo", itemNo++);
            m.put("MaterialCode", e.getMaterialCode());
            m.put("BatchCode", null);
            m.put("Quantity", e.getQuantity());
            m.put("UnitCode", e.getUnitCode());
            m.put("PlanCode", e.getPlanCode() != null ? e.getPlanCode() : "");
            m.put("ProductionStationCode", e.getProductionStationCode() != null ? e.getProductionStationCode() : "3SC01");
            m.put("OutWarehouseCode", "2010");
            wmsEntries.add(m);
        }
        data.put("Entries", wmsEntries);

        // 打印完整 JSON 到日志
        try {
            String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data);
            log.info("[WMS叫料] ====== 推送 WMS ProductionPicking JSON ======\n{}", json);
        } catch (Exception ignored) {}

        // 2. 推 WMS（不涉及事务）
        WmsPickingSendResponse resp;
        RuntimeException pushError = null;
        try {
            log.info("[WMS叫料] 推送 ProductionPicking billNo={}, 物料行数={}", billNo, wmsEntries.size());
            resp = wmsService.sendProductionPicking(data, request.getLineCode());
            log.info("[WMS叫料] 推送结果 Success={}, Message={}", resp.getSuccess(), resp.getMessage());
        } catch (RuntimeException ex) {
            log.error("[WMS叫料] 推送异常 billNo={}: {}", billNo, ex.getMessage());
            pushError = ex;
            resp = null;
        }

        // 3. 无论成功失败都存本地库（编程式事务，强制 commit）
        LocalDateTime now = LocalDateTime.now();
        final String finalProductCode = productCode;
        final String finalProductName = productName;
        final String finalStatus = resp != null && Boolean.TRUE.equals(resp.getSuccess()) ? "sent" : "failed";
        final String finalMessage;
        final String finalRequestId;
        final String finalWmsTaskNo;
        if (resp != null) {
            finalMessage = resp.getMessage();
            finalRequestId = resp.getData() != null ? resp.getData().getRequestId() : null;
            finalWmsTaskNo = resp.getData() != null ? resp.getData().getWmsTaskNo() : null;
        } else {
            finalMessage = pushError != null ? pushError.getMessage() : "推送异常";
            finalRequestId = null;
            finalWmsTaskNo = null;
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        WmsPicking head = tx.execute(status -> {
            WmsPicking h = new WmsPicking();
            h.setBillNo(billNo);
            h.setProductionNo(request.getProductionNo());
            h.setProductCode(finalProductCode);
            h.setProductName(finalProductName);
            h.setLineCode(request.getLineCode());
            h.setOrgCode(request.getOrgCode());
            h.setDeptCode(request.getDeptCode());
            h.setStatus(finalStatus);
            h.setMessage(finalMessage);
            h.setRequestId(finalRequestId);
            h.setWmsTaskNo(finalWmsTaskNo);
            h.setSendTime(now);
            h.setCreateTime(now);
            h.setUpdateTime(now);
            pickingMapper.insert(h);

            int iNo = 1;
            for (WmsPickingSendRequest.Entry e : request.getEntries()) {
                WmsPickingEntry entry = new WmsPickingEntry();
                entry.setPickingId(h.getId());
                entry.setItemNo(iNo++);
                entry.setMaterialCode(e.getMaterialCode());
                entry.setMaterialName(e.getMaterialName());
                entry.setPickedQty(e.getQuantity());
                entry.setUnitCode(e.getUnitCode());
                entry.setPlanCode(e.getPlanCode());
                entry.setStationCode(e.getProductionStationCode());
                entry.setWarehouseCode(e.getOutWarehouseCode());
                entry.setCreateTime(now);
                entry.setUpdateTime(now);
                entryMapper.insert(entry);
            }
            return h;
        });
        log.info("[WMS叫料] 存库完成 id={}, status={}", head != null ? head.getId() : -1, finalStatus);

        // 4. 推送失败时抛 BusinessException，GlobalExceptionHandler 会把 message 返回前端
        if (!"sent".equals(finalStatus)) {
            throw new BusinessException("WMS 推送失败：" + finalMessage);
        }
        return head;
    }

    // ======================= 4. 查询历史 =======================

    /**
     * 发送领料单到 WMS
     * 流程：查 pending 记录 → 拼 WMS JSON → POST → 按 Success 更新状态
     */
    @Transactional
    public WmsPicking sendToWms(Long pickingId) {
        WmsPicking head = pickingMapper.selectById(pickingId);
        if (head == null) {
            throw new RuntimeException("领料单不存在 id=" + pickingId);
        }
        if ("sent".equals(head.getStatus())) {
            throw new RuntimeException("领料单已发送，不可重复发送");
        }

        List<WmsPickingEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<WmsPickingEntry>()
                .eq(WmsPickingEntry::getPickingId, pickingId)
                .orderByAsc(WmsPickingEntry::getItemNo));

        // 组装 WMS Data
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("BillCode", head.getBillNo());
        data.put("ProdctionNo", head.getProductionNo());  // WMS 接口字段名（注意用户给的拼写是 ProdctionNo）
        data.put("OrderTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "+08:00");
        data.put("OrganizationCode", head.getOrgCode() != null ? head.getOrgCode() : "102");
        data.put("DepartmentCode", head.getDeptCode() != null ? head.getDeptCode() : "1022016");
        data.put("OrderType", 1);
        data.put("DocumentType", "ProductionPicking");
        data.put("CustomerCode", null);
        data.put("CostCenterCode", head.getOrgCode() != null ? head.getOrgCode() : "102");
        data.put("LineCode", head.getLineCode());
        data.put("ProductCode", head.getProductCode());

        List<Map<String, Object>> wmsEntries = new ArrayList<>();
        for (WmsPickingEntry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ItemNo", e.getItemNo());
            m.put("MaterialCode", e.getMaterialCode());
            m.put("BatchCode", null);
            m.put("Quantity", e.getPickedQty());
            m.put("UnitCode", e.getUnitCode());
            m.put("PlanCode", e.getPlanCode() != null ? e.getPlanCode() : "");
            m.put("ProductionStationCode", e.getStationCode());
            m.put("OutWarehouseCode", e.getWarehouseCode());
            wmsEntries.add(m);
        }
        data.put("Entries", wmsEntries);

        try {
            WmsPickingSendResponse resp = wmsService.sendProductionPicking(data, head.getLineCode());

            // 成功：更新状态 + WMS 返回数据
            head.setStatus("sent");
            head.setMessage(resp.getMessage());
            head.setSendTime(LocalDateTime.now());
            if (resp.getData() != null) {
                head.setRequestId(resp.getData().getRequestId());
                head.setWmsTaskNo(resp.getData().getWmsTaskNo());
                // 如果 WMS 返回的 BillCode 跟我们给的不一样，保留它
                if (resp.getData().getBillCode() != null && !resp.getData().getBillCode().equals(head.getBillNo())) {
                    head.setBillNo(resp.getData().getBillCode());
                }
            }
        } catch (RuntimeException ex) {
            // 失败：标记 failed
            head.setStatus("failed");
            head.setMessage(ex.getMessage());
            head.setSendTime(LocalDateTime.now());
        }
        head.setUpdateTime(LocalDateTime.now());
        pickingMapper.updateById(head);
        return head;
    }

    // ======================= 4. 查询历史 =======================

    public Page<WmsPicking> page(int pageNum, int pageSize, String status) {
        Page<WmsPicking> page = pickingMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<WmsPicking>()
                        .eq(status != null && !status.isBlank(), WmsPicking::getStatus, status)
                        .orderByDesc(WmsPicking::getCreateTime));
        // 批量填充 entries
        if (page.getRecords() != null && !page.getRecords().isEmpty()) {
            List<Long> pickingIds = page.getRecords().stream().map(WmsPicking::getId).collect(Collectors.toList());
            List<WmsPickingEntry> all = entryMapper.selectList(new LambdaQueryWrapper<WmsPickingEntry>()
                    .in(WmsPickingEntry::getPickingId, pickingIds)
                    .orderByAsc(WmsPickingEntry::getItemNo));
            Map<Long, List<WmsPickingEntry>> grouped = all.stream()
                    .collect(Collectors.groupingBy(WmsPickingEntry::getPickingId));
            page.getRecords().forEach(h -> h.setEntries(grouped.getOrDefault(h.getId(), new ArrayList<>())));
        }
        return page;
    }

    public List<WmsPickingEntry> listEntries(Long pickingId) {
        return entryMapper.selectList(new LambdaQueryWrapper<WmsPickingEntry>()
                .eq(WmsPickingEntry::getPickingId, pickingId)
                .orderByAsc(WmsPickingEntry::getItemNo));
    }

    // ======================= 工具 =======================

    private String generateBillNo() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return "MES-PK-" + now + "-" + suffix;
    }
}
