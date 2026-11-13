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
import com.enterprise.brain.task.wms.picktask.mapper.WmsPickTaskLogMapper;
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
    private WmsPickTaskLogMapper pickTaskLogMapper;

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
        String fieldKeys = "FEntity_FEntryID,FBillNo,FMOBillNO,FMaterialID.fnumber,FMaterialID.fname," +
                "FMaterialID2.fnumber,FMaterialID2.fname,FNeedQty2,FPickedQty,FUnitID2.fnumber," +
                "FUnitID2.fname,FMTONO,FPickStockId";

        log.info("[WMS叫料] 查金蝶 PRD_PPBOM filter={}", filter);
        List<JsonNode> rows = kingdeeService.executeBillQuery("PRD_PPBOM", fieldKeys, filter,
                null, 500);

        List<WmsPpbomRowVO> result = new ArrayList<>();
        int totalRowCount = 0;
        int filteredOut = 0;
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 13) continue;
            totalRowCount++;
            BigDecimal needQty = kingdeeService.decimalOf(row.get(7));
            BigDecimal pickedQty = kingdeeService.decimalOf(row.get(8));

            // [临时关闭] 仅保留 应发数量 > 已领数量 的行 —— 先全部显示，由剩余可领=应发-已领-叫料已推-定时承诺 来拦截
            // BigDecimal safeNeed = needQty != null ? needQty : BigDecimal.ZERO;
            // BigDecimal safePicked = pickedQty != null ? pickedQty : BigDecimal.ZERO;
            // if (safeNeed.compareTo(safePicked) <= 0) {
            //     filteredOut++;
            //     continue;
            // }

            WmsPpbomRowVO vo = new WmsPpbomRowVO();
            vo.setItemId(kingdeeService.textOf(row.get(0)));
            vo.setPpbomBillNo(kingdeeService.textOf(row.get(1)));
            vo.setProductionNo(kingdeeService.textOf(row.get(2)));
            vo.setProductCode(kingdeeService.textOf(row.get(3)));
            vo.setProductName(kingdeeService.textOf(row.get(4)));
            vo.setMaterialCode(kingdeeService.textOf(row.get(5)));
            vo.setMaterialName(kingdeeService.textOf(row.get(6)));
            vo.setNeedQty(needQty);
            vo.setPickedQty(pickedQty);
            vo.setUnitNumber(kingdeeService.textOf(row.get(9)));
            vo.setUnitName(kingdeeService.textOf(row.get(10)));
            vo.setPlanCode(kingdeeService.textOf(row.get(11)));
            vo.setPickStock(kingdeeService.textOf(row.get(12)));
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
            fillBoxStandardQty(result);
        }
        return result;
    }

    /**
     * 查金蝶物料基本信息（BD_MATERIAL），按物料编码取 FBoxStandardQty（标准装箱数量）
     * 作为前端"单次发料数量"的默认值
     */
    private void fillBoxStandardQty(List<WmsPpbomRowVO> ppbomRows) {
        List<String> materialCodes = ppbomRows.stream()
                .map(WmsPpbomRowVO::getMaterialCode)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (materialCodes.isEmpty()) return;

        // BD_MATERIAL 按 FNumber 查 FBoxStandardQty
        // 分批查，每批 ≤50 个：金蝶 ExecuteBillQuery Limit 会先排序再截断，
        // 一次塞太多物料会导致排在后面的被截掉，默认值=1。
        final int BATCH_SIZE = 50;
        Map<String, BigDecimal> boxMap = new HashMap<>();
        int totalRows = 0;
        for (int offset = 0; offset < materialCodes.size(); offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, materialCodes.size());
            List<String> batch = materialCodes.subList(offset, end);

            StringBuilder inClause = new StringBuilder();
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) inClause.append(',');
                inClause.append('\'').append(batch.get(i).replace("'", "''")).append('\'');
            }
            String filter = "FNumber in (" + inClause + ")";
            // Limit 放宽到批次大小的 10 倍，防金蝶返回重复行时截断有效值
            int limit = Math.max(200, batch.size() * 10);

            try {
                List<JsonNode> rows = kingdeeService.executeBillQuery("BD_MATERIAL",
                        "FNumber,FBoxStandardQty", filter, null, limit);
                totalRows += rows.size();
                for (JsonNode row : rows) {
                    if (!row.isArray() || row.size() < 2) continue;
                    String mc = kingdeeService.textOf(row.get(0));
                    BigDecimal qty = kingdeeService.decimalOf(row.get(1));
                    if (mc != null && qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                        boxMap.put(mc.trim(), qty);
                    }
                }
            } catch (Exception e) {
                log.warn("[WMS叫料] BD_MATERIAL 批次查询(offset={})失败: {}", offset, e.getMessage());
            }
        }
        log.info("[WMS叫料] 查金蝶 BD_MATERIAL FBoxStandardQty 物料数={} 批次={} 返回行={} 命中={}",
                materialCodes.size(), (materialCodes.size() + BATCH_SIZE - 1) / BATCH_SIZE,
                totalRows, boxMap.size());

        for (WmsPpbomRowVO vo : ppbomRows) {
            String key = vo.getMaterialCode() == null ? null : vo.getMaterialCode().trim();
            BigDecimal box = boxMap.get(key);
            if (box == null && key != null) {
                // 再试大写 / 小写
                box = boxMap.get(key.toUpperCase());
                if (box == null) box = boxMap.get(key.toLowerCase());
            }
            vo.setBoxStandardQty(box != null ? box : BigDecimal.ONE);
        }
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
            // remaining = 应发 - 叫料已推 - 定时任务承诺总量（不扣减已领数量）
            BigDecimal remaining = need.subtract(pushed).subtract(ptTotal);
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

    /** 金蝶分录内码 FEntity_FEntryID 转 int，null/空/非数字 → 0 */
    private int toIntOrZero(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ex) { return 0; }
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

    /**
     * 生成唯一 BillCode：{productionNo}-{MMddHHmmssSSS}
     * 精度到毫秒，同一毫秒冲突时拼 2 位随机数兜底
     * 防御性截断：productionNo 过长时保留尾部（序号后缀更有辨识度），BillCode 总长 ≤ 80
     */
    public String generateNextBillNo(String productionNo) {
        if (productionNo == null || productionNo.isBlank()) productionNo = "UNKNOWN";
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmssSSS"));
        final int MAX_LEN = 80;
        final int tsLen = ts.length() + 1; // +1 是连字符 "-"
        String prod = productionNo;
        if (prod.length() + tsLen > MAX_LEN) {
            prod = prod.substring(prod.length() - (MAX_LEN - tsLen));
        }
        String billNo = prod + "-" + ts;
        log.info("[BillCode] 生成 → {}", billNo);
        return billNo;
    }

    /**
     * 获取指定生产订单号 + 物料编码的本地累计数据汇总（单物料）
     * 清除后前端用来刷新查询结果表里的那一行
     */
    public LocalQtySummary getLocalQtySummaryForMaterial(String productionNo, String materialCode) {
        LocalQtySummary s = getLocalQtySummary(productionNo).get(materialCode);
        return s != null ? s : new LocalQtySummary();
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
            entry.setItemId(e.getItemId());
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
        String billNo = generateNextBillNo(request.getProductionNo());

        // [临时关闭] 校验：本次领料数量不能超过 应发数量 - 已领 - 叫料已推 - 定时承诺
        // validatePickingQty(request);

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

        // 直接传 entries，不做批次拆分，BatchCode=null 让 WMS 侧处理
        List<Map<String, Object>> wmsEntries = new ArrayList<>();
        int itemNo = 1;
        for (WmsPickingSendRequest.Entry e : request.getEntries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ItemNo", itemNo++);
            m.put("ItemId", toIntOrZero(e.getItemId()));
            m.put("MaterialCode", e.getMaterialCode());
            m.put("BatchCode", null);
            m.put("Quantity", safe(e.getQuantity()));
            m.put("UnitCode", e.getUnitCode() != null ? e.getUnitCode() : "");
            m.put("PlanCode", e.getPlanCode() != null ? e.getPlanCode() : "");
            m.put("ProductionStationCode", e.getProductionStationCode() != null ? e.getProductionStationCode() : "3SC01");
            m.put("OutWarehouseCode", e.getOutWarehouseCode() != null ? e.getOutWarehouseCode() : "2010");
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
                entry.setItemId(e.getItemId());
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
            m.put("ItemId", toIntOrZero(e.getItemId()));
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

    public Page<WmsPicking> page(int pageNum, int pageSize, String status, String keyword, String lineCode) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        Page<WmsPicking> page = pickingMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<WmsPicking>()
                        .eq(status != null && !status.isBlank(), WmsPicking::getStatus, status)
                        .eq(lineCode != null && !lineCode.isBlank(), WmsPicking::getLineCode, lineCode)
                        .ge(WmsPicking::getCreateTime, weekAgo)
                        .and(kw != null, w -> w
                                .like(WmsPicking::getBillNo, kw)
                                .or().like(WmsPicking::getProductionNo, kw)
                                .or().like(WmsPicking::getWmsTaskNo, kw))
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

    // ======================= 5. 按物料清除本地累计（叫料已推 + 定时承诺） =======================

    /**
     * 清除指定生产订单下某物料的所有本地累计数据：
     *  - wms_picking_entry 中 materialCode 匹配的记录（关联的 picking 头如空则一并删）
     *  - wms_pick_task_entry 中 materialCode 匹配的记录（关联的 pick_task 头如空则一并删）
     *
     * 作用：将该物料的"叫料已推"和"定时承诺"重置为 0，让前端重新开始叫料。
     *
     * @return 被清除的 picking_entry 数量 + pick_task_entry 数量
     */
    @Transactional
    public int clearMaterialSummary(String productionNo, String materialCode) {
        if (productionNo == null || productionNo.isBlank()
                || materialCode == null || materialCode.isBlank()) {
            throw new BusinessException("生产订单号 和 物料编码 不能为空");
        }
        String mc = materialCode.trim();

        int totalCleared = 0;

        // ---- 1. 清除叫料已推：wms_picking_entry ----
        // 找到该 productionNo 下所有 picking 头
        List<WmsPicking> allPickings = pickingMapper.selectList(
                new LambdaQueryWrapper<WmsPicking>()
                        .eq(WmsPicking::getProductionNo, productionNo));

        if (!allPickings.isEmpty()) {
            List<Long> pickingIds = allPickings.stream().map(WmsPicking::getId).collect(Collectors.toList());
            // 删掉 materialCode 匹配的 entries
            int pickingEntryDeleted = entryMapper.delete(new LambdaQueryWrapper<WmsPickingEntry>()
                    .in(WmsPickingEntry::getPickingId, pickingIds)
                    .eq(WmsPickingEntry::getMaterialCode, mc));
            totalCleared += pickingEntryDeleted;
            log.info("[WMS叫料·清除] 删除叫料明细 entry={} 条 productionNo={} materialCode={}",
                    pickingEntryDeleted, productionNo, mc);

            // 如果某个 picking 头下已经没有 entries 了，把整个头删掉
            List<WmsPickingEntry> remainingEntries = entryMapper.selectList(
                    new LambdaQueryWrapper<WmsPickingEntry>()
                            .in(WmsPickingEntry::getPickingId, pickingIds));
            Set<Long> remainingPickingIds = remainingEntries.stream()
                    .map(WmsPickingEntry::getPickingId).collect(Collectors.toSet());
            for (WmsPicking h : allPickings) {
                if (!remainingPickingIds.contains(h.getId())) {
                    pickingMapper.deleteById(h.getId());
                    log.info("[WMS叫料·清除] 领料单头已空，删除 billNo={} id={}", h.getBillNo(), h.getId());
                }
            }
        }

        // ---- 2. 清除定时承诺：wms_pick_task_entry ----
        List<WmsPickTask> allTasks = pickTaskMapper.selectList(
                new LambdaQueryWrapper<WmsPickTask>()
                        .eq(WmsPickTask::getProductionNo, productionNo));

        if (!allTasks.isEmpty()) {
            List<Long> taskIds = allTasks.stream().map(WmsPickTask::getId).collect(Collectors.toList());
            int taskEntryDeleted = pickTaskEntryMapper.delete(new LambdaQueryWrapper<WmsPickTaskEntry>()
                    .in(WmsPickTaskEntry::getTaskId, taskIds)
                    .eq(WmsPickTaskEntry::getMaterialCode, mc));
            totalCleared += taskEntryDeleted;
            log.info("[WMS叫料·清除] 删除定时任务明细 entry={} 条 productionNo={} materialCode={}",
                    taskEntryDeleted, productionNo, mc);

            // 如果某个 pick_task 头下已经没有 entries 了，把整个头删掉
            List<WmsPickTaskEntry> remainingTaskEntries = pickTaskEntryMapper.selectList(
                    new LambdaQueryWrapper<WmsPickTaskEntry>()
                            .in(WmsPickTaskEntry::getTaskId, taskIds));
            Set<Long> remainingTaskIds = remainingTaskEntries.stream()
                    .map(WmsPickTaskEntry::getTaskId).collect(Collectors.toSet());
            for (WmsPickTask t : allTasks) {
                if (!remainingTaskIds.contains(t.getId())) {
                    pickTaskMapper.deleteById(t.getId());
                    log.info("[WMS叫料·清除] 定时任务头已空，删除 taskNo={} id={}", t.getTaskNo(), t.getId());
                }
            }
        }

        if (totalCleared == 0) {
            log.info("[WMS叫料·清除] 未找到可清除的数据 productionNo={} materialCode={}", productionNo, mc);
        }
        return totalCleared;
    }

    // ======================= 工具 =======================

    private String generateBillNo() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return "MES-PK-" + now + "-" + suffix;
    }
}
