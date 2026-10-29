package com.enterprise.brain.task.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.service.KingdeeService;
import com.enterprise.brain.task.service.KingdeeService.StockRow;
import com.enterprise.brain.task.service.WmsService;
import com.enterprise.brain.task.wms.dto.WmsPpbomRowVO;
import com.enterprise.brain.task.wms.dto.WmsPickingSendRequest;
import com.enterprise.brain.task.wms.dto.WmsPickingSendResponse;
import com.enterprise.brain.task.wms.entity.WmsPicking;
import com.enterprise.brain.task.wms.entity.WmsPickingEntry;
import com.enterprise.brain.task.wms.mapper.WmsPickingEntryMapper;
import com.enterprise.brain.task.wms.mapper.WmsPickingMapper;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTask;
import com.enterprise.brain.task.wms.picktask.entity.WmsPickTaskEntry;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskEntryMapper;
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskMapper;
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
    private WmsPickTaskMapper pickTaskMapper;

    @Resource
    private WmsPickTaskEntryMapper pickTaskEntryMapper;

    @Resource
    private KingdeeService kingdeeService;

    @Resource
    private WmsService wmsService;

    @Resource
    private PlatformTransactionManager transactionManager;

    // ======================= 1. 查金蝶 PRD_PPBOM（增强：带本地累计） =======================

    /**
     * 按生产订单号（PRD_PPBOM.FMOBillNO）查询子项物料明细
     * FieldKeys 顺序: FBillNO, FMOBillNO, FMaterialID.fnumber, FMaterialID.fname,
     *                 FMaterialID2.fnumber, FMaterialID2.fname, FNeedQty2, FPickedQty,
     *                 FUnitID2.fnumber, FUnitID2.fname, FMTONO, FPickStockId
     *
     * 仅保留 FNeedQty2 > FPickedQty 的行（应发数量 > 已领数量）
     *
     * 返回的 VO 额外填充：pushedQty（叫料已推送）、pickTaskTotalQty（定时任务总量）、
     * pickTaskSentQty（定时已发）、remainingQty（剩余可领 = needQty - pushedQty - pickTaskTotalQty）
     *
     * @param productionNo 生产订单号
     * @param fillStock 是否填充即时库存（STK_Inventory，仓库2010），false 可跳过以减少金蝶调用
     */
    public List<WmsPpbomRowVO> queryPpbomByProductionNo(String productionNo, boolean fillStock) {
        if (productionNo == null || productionNo.isBlank()) {
            return new ArrayList<>();
        }
        String filter = "FMOBillNO = '" + productionNo.replace("'", "''") + "'";
        String fieldKeys = "FBillNo,FMOBillNO,FMaterialID.fnumber,FMaterialID.fname," +
                "FMaterialID2.fnumber,FMaterialID2.fname,FNeedQty2,FPickedQty,FUnitID2.fnumber," +
                "FUnitID2.fname,FMTONO,FPickStockId";

        log.info("[WMS叫料] 查金蝶 PRD_PPBOM filter={}", filter);
        List<JsonNode> rows = kingdeeService.executeBillQuery("PRD_PPBOM", fieldKeys, filter,
                null, 500);

        List<WmsPpbomRowVO> result = new ArrayList<>();
        int totalRowCount = 0;
        int filteredOut = 0;
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 12) continue;
            totalRowCount++;
            BigDecimal needQty = kingdeeService.decimalOf(row.get(6));
            BigDecimal pickedQty = kingdeeService.decimalOf(row.get(7));

            // 仅保留 应发数量 > 已领数量 的行
            BigDecimal safeNeed = needQty != null ? needQty : BigDecimal.ZERO;
            BigDecimal safePicked = pickedQty != null ? pickedQty : BigDecimal.ZERO;
            if (safeNeed.compareTo(safePicked) <= 0) {
                filteredOut++;
                continue;
            }

            WmsPpbomRowVO vo = new WmsPpbomRowVO();
            vo.setPpbomBillNo(kingdeeService.textOf(row.get(0)));
            vo.setProductionNo(kingdeeService.textOf(row.get(1)));
            vo.setProductCode(kingdeeService.textOf(row.get(2)));
            vo.setProductName(kingdeeService.textOf(row.get(3)));
            vo.setMaterialCode(kingdeeService.textOf(row.get(4)));
            vo.setMaterialName(kingdeeService.textOf(row.get(5)));
            vo.setNeedQty(needQty);
            vo.setPickedQty(pickedQty);
            vo.setUnitNumber(kingdeeService.textOf(row.get(8)));
            vo.setUnitName(kingdeeService.textOf(row.get(9)));
            vo.setPlanCode(kingdeeService.textOf(row.get(10)));
            vo.setPickStock(kingdeeService.textOf(row.get(11)));
            // 先设默认值，后面统一填充
            vo.setPushedQty(BigDecimal.ZERO);
            vo.setPickTaskTotalQty(BigDecimal.ZERO);
            vo.setPickTaskSentQty(BigDecimal.ZERO);
            vo.setRemainingQty(vo.getNeedQty() != null ? vo.getNeedQty() : BigDecimal.ZERO);
            result.add(vo);
        }
        log.info("[WMS叫料] PRD_PPBOM 返回 {} 条，过滤应发≤已领 {} 条，最终 {} 条",
                totalRowCount, filteredOut, result.size());

        // 填充本地累计数据
        if (!result.isEmpty()) {
            fillLocalQtySummary(productionNo, result);
            if (fillStock) {
                fillImmediateStock(result);
            }
        }
        return result;
    }

    /**
     * 查金蝶即时库存（STK_Inventory），过滤 FStockId.FNumber='2010'，按物料编码汇总 FBaseQty
     */
    private void fillImmediateStock(List<WmsPpbomRowVO> ppbomRows) {
        // 收集所有不重复的物料编码
        List<String> materialCodes = ppbomRows.stream()
                .map(WmsPpbomRowVO::getMaterialCode)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (materialCodes.isEmpty()) return;

        // 构造 FMaterialId.FNumber in (...)
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < materialCodes.size(); i++) {
            if (i > 0) inClause.append(',');
            inClause.append('\'').append(materialCodes.get(i).replace("'", "''")).append('\'');
        }
        String filter = "FMaterialId.FNumber in (" + inClause + ") and FStockId.FNumber='2010' and FBaseQty>0";

        Map<String, BigDecimal> stockMap = new HashMap<>();
        try {
            List<JsonNode> rows = kingdeeService.executeBillQuery("STK_Inventory",
                    "FMaterialId.FNumber,FBaseQty",
                    filter, null, 5000);
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 2) continue;
                String mc = kingdeeService.textOf(row.get(0));
                BigDecimal qty = kingdeeService.decimalOf(row.get(1));
                stockMap.merge(mc, qty != null ? qty : BigDecimal.ZERO, BigDecimal::add);
            }
            log.info("[WMS叫料] 查即时库存 仓库=2010 物料数={} 有库存物料={}", materialCodes.size(), stockMap.size());
        } catch (Exception e) {
            log.warn("[WMS叫料] 查即时库存失败，跳过填充: {}", e.getMessage());
        }

        for (WmsPpbomRowVO vo : ppbomRows) {
            String mc = vo.getMaterialCode();
            vo.setImmediateStockQty(stockMap.getOrDefault(mc, BigDecimal.ZERO));
        }
    }

    /**
     * 填充本地累计数据到 PPBOM 查询结果：
     * - pushedQty: 叫料已推送（wms_picking_entry.pickedQty 汇总，wms_picking.status=sent）
     * - pickTaskTotalQty: 定时任务总量（wms_pick_task_entry.totalQty，排除 cancelled 任务）
     * - pickTaskSentQty: 定时已发（wms_pick_task_entry.sentQty）
     * - remainingQty: 剩余可领 = needQty - pushedQty - pickTaskSentQty
     */
    private void fillLocalQtySummary(String productionNo, List<WmsPpbomRowVO> ppbomRows) {
        // 1. 查叫料已推送
        List<WmsPicking> sentPickings = pickingMapper.selectList(
                new LambdaQueryWrapper<WmsPicking>()
                        .eq(WmsPicking::getProductionNo, productionNo)
                        .eq(WmsPicking::getStatus, "sent"));
        Map<String, BigDecimal> pushedMap = new HashMap<>();
        if (!sentPickings.isEmpty()) {
            List<Long> pickingIds = sentPickings.stream().map(WmsPicking::getId).collect(Collectors.toList());
            List<WmsPickingEntry> sentEntries = entryMapper.selectList(
                    new LambdaQueryWrapper<WmsPickingEntry>()
                            .in(WmsPickingEntry::getPickingId, pickingIds));
            for (WmsPickingEntry e : sentEntries) {
                pushedMap.merge(e.getMaterialCode(), safe(e.getPickedQty()), BigDecimal::add);
            }
        }

        // 2. 注定时任务总量 & 已发（排除 cancelled）
        List<WmsPickTask> activeTasks = pickTaskMapper.selectList(
                new LambdaQueryWrapper<WmsPickTask>()
                        .eq(WmsPickTask::getProductionNo, productionNo)
                        .ne(WmsPickTask::getStatus, "cancelled"));
        Map<String, BigDecimal> pickTaskTotalMap = new HashMap<>();
        Map<String, BigDecimal> pickTaskSentMap = new HashMap<>();
        if (!activeTasks.isEmpty()) {
            List<Long> taskIds = activeTasks.stream().map(WmsPickTask::getId).collect(Collectors.toList());
            List<WmsPickTaskEntry> taskEntries = pickTaskEntryMapper.selectList(
                    new LambdaQueryWrapper<WmsPickTaskEntry>()
                            .in(WmsPickTaskEntry::getTaskId, taskIds));
            for (WmsPickTaskEntry e : taskEntries) {
                pickTaskTotalMap.merge(e.getMaterialCode(), safe(e.getTotalQty()), BigDecimal::add);
                pickTaskSentMap.merge(e.getMaterialCode(), safe(e.getSentQty()), BigDecimal::add);
            }
        }

        // 3. 填充到 VO
        for (WmsPpbomRowVO vo : ppbomRows) {
            String mc = vo.getMaterialCode();
            BigDecimal pushed = pushedMap.getOrDefault(mc, BigDecimal.ZERO);
            BigDecimal ptTotal = pickTaskTotalMap.getOrDefault(mc, BigDecimal.ZERO);
            BigDecimal ptSent = pickTaskSentMap.getOrDefault(mc, BigDecimal.ZERO);
            BigDecimal need = vo.getNeedQty() != null ? vo.getNeedQty() : BigDecimal.ZERO;
            BigDecimal picked = vo.getPickedQty() != null ? vo.getPickedQty() : BigDecimal.ZERO;
            // remaining = 应发 - 已领 - 叫料已推 - 定时任务承诺总量
            BigDecimal remaining = need.subtract(picked).subtract(pushed).subtract(ptTotal);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                remaining = BigDecimal.ZERO;
            }
            vo.setPushedQty(pushed);
            vo.setPickTaskTotalQty(ptTotal);
            vo.setPickTaskSentQty(ptSent);
            vo.setRemainingQty(remaining);
        }
        log.info("[WMS叫料] 填充本地累计 productionNo={}: ppbom行数={}, 有叫料推送物料={}, 有定时任务物料={}",
                productionNo, ppbomRows.size(), pushedMap.size(), pickTaskTotalMap.size());
    }

    /**
     * 获取指定生产订单号 + 物料编码的本地累计数据汇总（用于校验）
     * @return Map<materialCode, LocalQtySummary>
     */
    public Map<String, LocalQtySummary> getLocalQtySummary(String productionNo) {
        Map<String, LocalQtySummary> result = new HashMap<>();

        // 叫料已推送
        List<WmsPicking> sentPickings = pickingMapper.selectList(
                new LambdaQueryWrapper<WmsPicking>()
                        .eq(WmsPicking::getProductionNo, productionNo)
                        .eq(WmsPicking::getStatus, "sent"));
        if (!sentPickings.isEmpty()) {
            List<Long> pickingIds = sentPickings.stream().map(WmsPicking::getId).collect(Collectors.toList());
            List<WmsPickingEntry> sentEntries = entryMapper.selectList(
                    new LambdaQueryWrapper<WmsPickingEntry>()
                            .in(WmsPickingEntry::getPickingId, pickingIds));
            for (WmsPickingEntry e : sentEntries) {
                LocalQtySummary s = result.computeIfAbsent(e.getMaterialCode(), k -> new LocalQtySummary());
                s.pushedQty = s.pushedQty.add(safe(e.getPickedQty()));
            }
        }

        // 定时任务总量 & 已发（排除 cancelled）
        List<WmsPickTask> activeTasks = pickTaskMapper.selectList(
                new LambdaQueryWrapper<WmsPickTask>()
                        .eq(WmsPickTask::getProductionNo, productionNo)
                        .ne(WmsPickTask::getStatus, "cancelled"));
        if (!activeTasks.isEmpty()) {
            List<Long> taskIds = activeTasks.stream().map(WmsPickTask::getId).collect(Collectors.toList());
            List<WmsPickTaskEntry> taskEntries = pickTaskEntryMapper.selectList(
                    new LambdaQueryWrapper<WmsPickTaskEntry>()
                            .in(WmsPickTaskEntry::getTaskId, taskIds));
            for (WmsPickTaskEntry e : taskEntries) {
                LocalQtySummary s = result.computeIfAbsent(e.getMaterialCode(), k -> new LocalQtySummary());
                s.pickTaskTotalQty = s.pickTaskTotalQty.add(safe(e.getTotalQty()));
                s.pickTaskSentQty = s.pickTaskSentQty.add(safe(e.getSentQty()));
            }
        }
        return result;
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * 校验叫料推送数量不能超过应发数量
     * 已领 + 叫料已推送 + 定时任务承诺总量 + 本次领料 ≤ 金蝶应发数量
     *
     * 说明：pickedQty 是金蝶已领（FPickedQty），pushedQty 是本地叫料已推，
     *       pickTaskTotalQty 是定时任务承诺发料量，三者一起预占额度。
     */
    private void validatePickingQty(WmsPickingSendRequest request) {
        if (request.getEntries() == null || request.getEntries().isEmpty()) return;

        // 查金蝶 PPBOM（内部已自动填充 pickedQty/pushedQty/pickTaskTotalQty/pickTaskSentQty）
        List<WmsPpbomRowVO> ppbomList = queryPpbomByProductionNo(request.getProductionNo(), false);
        Map<String, WmsPpbomRowVO> ppbomMap = new HashMap<>();
        for (WmsPpbomRowVO vo : ppbomList) {
            ppbomMap.put(vo.getMaterialCode(), vo);
        }

        List<String> errors = new ArrayList<>();
        for (WmsPickingSendRequest.Entry e : request.getEntries()) {
            String mc = e.getMaterialCode();
            BigDecimal qty = safe(e.getQuantity());
            WmsPpbomRowVO vo = ppbomMap.get(mc);
            if (vo == null) {
                errors.add("物料 " + mc + " 在金蝶用料清单中不存在，无法校验");
                continue;
            }
            BigDecimal needQty = safe(vo.getNeedQty());
            BigDecimal pickedQty = safe(vo.getPickedQty());
            BigDecimal pushedQty = safe(vo.getPushedQty());
            BigDecimal ptTotal = safe(vo.getPickTaskTotalQty());
            BigDecimal totalOccupied = pickedQty.add(pushedQty).add(ptTotal);
            BigDecimal newTotal = totalOccupied.add(qty);
            log.info("[WMS叫料] 校验物料={} 应发={} 已领={} 叫料已推={} 定时承诺={} 预占合计={} 本次={} 新合计={}",
                    mc, needQty, pickedQty, pushedQty, ptTotal, totalOccupied, qty, newTotal);
            if (newTotal.compareTo(needQty) > 0) {
                BigDecimal over = newTotal.subtract(needQty);
                BigDecimal remaining = needQty.subtract(totalOccupied);
                errors.add(String.format(
                        "物料 %s 本次领料 %.4f 超量：金蝶应发 %.4f，已领 %.4f，叫料已推 %.4f，定时任务承诺 %.4f，剩余可领 %.4f，超出 %.4f",
                        mc, qty, needQty, pickedQty, pushedQty, ptTotal,
                        remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining, over));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("领料数量校验未通过:\n" + String.join("\n", errors));
        }
        log.info("[WMS叫料] 校验通过 productionNo={}, entries={}", request.getProductionNo(), request.getEntries().size());
    }

    /** 本地累计数量汇总（用于校验和展示） */
    public static class LocalQtySummary {
        /** 叫料已推送数量（status=sent） */
        public BigDecimal pushedQty = BigDecimal.ZERO;
        /** 定时任务发料总数量（排除 cancelled 任务） */
        public BigDecimal pickTaskTotalQty = BigDecimal.ZERO;
        /** 定时任务已发数量 */
        public BigDecimal pickTaskSentQty = BigDecimal.ZERO;

        /** 已占用总量（叫料已推 + 定时已发） */
        public BigDecimal totalPushed() {
            return pushedQty.add(pickTaskSentQty);
        }
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

        // ============ 0. 校验：本次领料数量不能超过 应发数量 - 已推送 - 定时已发 ============
        validatePickingQty(request);

        // 1. 从 entries 拿产品编码（前端查 PRD_PPBOM 时已经带下来了，同一订单相同）
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

        // FIFO 批次拆分：一个批次一条 entry
        Map<String, BigDecimal> qtyMap = new LinkedHashMap<>();
        Map<String, String> unitMap = new LinkedHashMap<>();
        Map<String, String> planMap = new LinkedHashMap<>();
        Map<String, String> stationMap = new LinkedHashMap<>();
        Map<String, String> whMap = new LinkedHashMap<>();
        for (WmsPickingSendRequest.Entry e : request.getEntries()) {
            qtyMap.put(e.getMaterialCode(), safe(e.getQuantity()));
            unitMap.put(e.getMaterialCode(), e.getUnitCode() != null ? e.getUnitCode() : "");
            planMap.put(e.getMaterialCode(), e.getPlanCode() != null ? e.getPlanCode() : "");
            stationMap.put(e.getMaterialCode(), e.getProductionStationCode() != null ? e.getProductionStationCode() : "3SC01");
            whMap.put(e.getMaterialCode(), e.getOutWarehouseCode() != null ? e.getOutWarehouseCode() : "2010");
        }
        List<Map<String, Object>> wmsEntries = fifoSplitWmsEntries(qtyMap, unitMap, planMap, stationMap, whMap);
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
            log.info("[WMS叫料] 推送 ProductionPicking billNo={}, FIFO拆分后行数={}, 原物料行数={}",
                    billNo, wmsEntries.size(), request.getEntries().size());
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

    // ======================= 3.5 FIFO 批次拆分 =======================

    /**
     * FIFO 批次拆分：查金蝶 STK_Inventory（FUpdateTime asc = 先入先出），
     * 按批次先后把每个物料的需求数量拆分到多条 WMS entry（一个批次一条）。
     * 查不到批次或库存不足时，剩余部分仍发 BatchCode=null 让 WMS 侧处理。
     *
     * @param materialQtyMap  物料编码 → 需要发的数量
     * @param unitCodeMap     物料编码 → 单位编码
     * @param planCodeMap     物料编码 → 计划跟踪号
     * @param stationCodeMap  物料编码 → 工位编码
     * @param warehouseCodeMap 物料编码 → 发料仓库（默认 2010）
     * @return FIFO 拆分后的 WMS entries（BatchCode 已填）
     */
    public List<Map<String, Object>> fifoSplitWmsEntries(
            Map<String, BigDecimal> materialQtyMap,
            Map<String, String> unitCodeMap,
            Map<String, String> planCodeMap,
            Map<String, String> stationCodeMap,
            Map<String, String> warehouseCodeMap) {

        List<Map<String, Object>> result = new ArrayList<>();
        if (materialQtyMap.isEmpty()) return result;

        List<String> materialCodes = new ArrayList<>(materialQtyMap.keySet());
        String firstWarehouse = warehouseCodeMap.values().stream().findFirst().orElse("2010");

        // 查金蝶 FIFO 批次库存（全仓库）
        List<StockRow> allStocks;
        try {
            allStocks = kingdeeService.queryStock(materialCodes);
        } catch (Exception e) {
            log.warn("[WMS叫料] 查金蝶 FIFO 批次失败，回退无批次发送: {}", e.getMessage());
            allStocks = new ArrayList<>();
        }

        // 只取发料仓库（优先用 entries 里的 OutWarehouseCode，默认 2010）
        Map<String, List<StockRow>> stockByMaterial = allStocks.stream()
                .filter(s -> firstWarehouse.equals(s.getStockNumber()))
                .collect(Collectors.groupingBy(StockRow::getMaterialCode, LinkedHashMap::new, Collectors.toList()));

        log.info("[WMS叫料] FIFO 批次拆分 materialCount={} stockRows={} (warehouse={})",
                materialCodes.size(), allStocks.size(), firstWarehouse);

        int itemNo = 1;
        for (String materialCode : materialCodes) {
            BigDecimal need = safe(materialQtyMap.get(materialCode));
            if (need.compareTo(BigDecimal.ZERO) <= 0) continue;

            List<StockRow> batches = stockByMaterial.getOrDefault(materialCode, new ArrayList<>());
            BigDecimal remaining = need;
            String myPlanCode = planCodeMap.get(materialCode);

            // 分两轮：第一轮 planCode 精确匹配，第二轮通用（未使用过的所有批次）
            Set<String> usedLotKeys = new HashSet<>();

            // ===== 第一轮：只吃 planCode 匹配的批次 =====
            if (myPlanCode != null && !myPlanCode.isBlank()) {
                for (StockRow batch : batches) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    // FMTONO 匹配（两边都不为空才比）
                    String batchPlan = batch.getPlanCode();
                    if (batchPlan == null || batchPlan.isBlank() || !myPlanCode.equals(batchPlan)) continue;
                    BigDecimal avail = safe(batch.getBaseQty());
                    if (avail.compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal thisQty = remaining.min(avail);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ItemNo", itemNo++);
                    m.put("MaterialCode", materialCode);
                    m.put("BatchCode", batch.getLotNumber());
                    m.put("Quantity", thisQty);
                    m.put("UnitCode", unitCodeMap.getOrDefault(materialCode, ""));
                    m.put("PlanCode", myPlanCode);
                    m.put("ProductionStationCode", stationCodeMap.getOrDefault(materialCode, "3SC01"));
                    m.put("OutWarehouseCode", warehouseCodeMap.getOrDefault(materialCode, firstWarehouse));
                    result.add(m);
                    remaining = remaining.subtract(thisQty);
                    usedLotKeys.add(batch.getLotNumber());

                    log.info("[WMS叫料] FIFO(plan) 物料={} 计划跟踪号={} 批次={} 可用={} 分配={} 剩余={}",
                            materialCode, myPlanCode, batch.getLotNumber(), avail, thisQty, remaining);
                }
            }

            // ===== 第二轮：通用批次补剩余（planCode 不匹配的 / 无计划跟踪号的批次）=====
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                for (StockRow batch : batches) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    // 跳过第一轮用过的
                    if (usedLotKeys.contains(batch.getLotNumber())) continue;
                    BigDecimal avail = safe(batch.getBaseQty());
                    if (avail.compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal thisQty = remaining.min(avail);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ItemNo", itemNo++);
                    m.put("MaterialCode", materialCode);
                    m.put("BatchCode", batch.getLotNumber());
                    m.put("Quantity", thisQty);
                    m.put("UnitCode", unitCodeMap.getOrDefault(materialCode, ""));
                    m.put("PlanCode", myPlanCode != null ? myPlanCode : "");
                    m.put("ProductionStationCode", stationCodeMap.getOrDefault(materialCode, "3SC01"));
                    m.put("OutWarehouseCode", warehouseCodeMap.getOrDefault(materialCode, firstWarehouse));
                    result.add(m);
                    remaining = remaining.subtract(thisQty);
                    usedLotKeys.add(batch.getLotNumber());

                    log.info("[WMS叫料] FIFO(通用) 物料={} 批次={} 可用={} 分配={} 剩余={}",
                            materialCode, batch.getLotNumber(), avail, thisQty, remaining);
                }
            }

            // 库存不足部分：仍发一条 BatchCode=null，让 WMS/ERP 侧处理
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("[WMS叫料] FIFO 物料={} 批次库存不足(plan={})，剩余 {} 回退无批次发送",
                        materialCode, myPlanCode, remaining);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ItemNo", itemNo++);
                m.put("MaterialCode", materialCode);
                m.put("BatchCode", null);
                m.put("Quantity", remaining);
                m.put("UnitCode", unitCodeMap.getOrDefault(materialCode, ""));
                m.put("PlanCode", myPlanCode != null ? myPlanCode : "");
                m.put("ProductionStationCode", stationCodeMap.getOrDefault(materialCode, "3SC01"));
                m.put("OutWarehouseCode", warehouseCodeMap.getOrDefault(materialCode, firstWarehouse));
                result.add(m);
            }
        }
        return result;
    }

    /**
     * FIFO 批次拆分重载：单个 Entry → 可能多条 WMS entries
     */
    public List<Map<String, Object>> fifoSplitWmsEntry(
            String materialCode, BigDecimal qty, String unitCode, String planCode,
            String stationCode, String warehouseCode) {
        Map<String, BigDecimal> qtyMap = new LinkedHashMap<>();
        qtyMap.put(materialCode, qty);
        Map<String, String> unitMap = new LinkedHashMap<>();
        unitMap.put(materialCode, unitCode);
        Map<String, String> planMap = new LinkedHashMap<>();
        planMap.put(materialCode, planCode);
        Map<String, String> stationMap = new LinkedHashMap<>();
        stationMap.put(materialCode, stationCode);
        Map<String, String> whMap = new LinkedHashMap<>();
        whMap.put(materialCode, warehouseCode);
        return fifoSplitWmsEntries(qtyMap, unitMap, planMap, stationMap, whMap);
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
