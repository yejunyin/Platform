package com.enterprise.brain.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.enterprise.brain.task.TaskServiceApplication;
import com.enterprise.brain.task.config.MaterialProperties;
import com.enterprise.brain.task.dto.request.MaterialAuditRequest;
import com.enterprise.brain.task.dto.request.MaterialSubmitRequest;
import com.enterprise.brain.task.dto.response.AuditListItemDTO;
import com.enterprise.brain.task.dto.response.MaterialApplicationDTO;
import com.enterprise.brain.task.dto.response.MaterialCallOpResultDTO;
import com.enterprise.brain.task.dto.response.MaterialResult;
import com.enterprise.brain.task.dto.response.OrderInfoDTO;
import com.enterprise.brain.task.dto.response.OrderMaterialsDTO;
import com.enterprise.brain.task.dto.response.QcStaffDTO;
import com.enterprise.brain.task.entity.DbMaterialCall;
import com.enterprise.brain.task.entity.DbMaterialCallBatch;
import com.enterprise.brain.task.entity.DbMaterialCallItem;
import com.enterprise.brain.task.entity.DbMaterialReason;
import com.enterprise.brain.task.mapper.DbMaterialCallBatchMapper;
import com.enterprise.brain.task.mapper.DbMaterialCallItemMapper;
import com.enterprise.brain.task.mapper.DbMaterialCallMapper;
import com.enterprise.brain.task.mapper.DbMaterialReasonMapper;
import com.enterprise.brain.task.service.KingdeeService;
import com.enterprise.brain.task.service.MaterialMessageService;
import com.enterprise.brain.task.service.MaterialReturnService;
import com.enterprise.brain.task.service.WmsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 补退料（生产补料/生产退料）服务实现
 * <p>状态机：10待质检审核 → (驳回)11 / (通过)20批次匹配中 → 30退料单生成中 → 40退料单已生成
 * → (WMS仓库)50WMS申请已生成/41WMS申请异常 → 99已完成；ERP生成失败→31可重试。</p>
 */
@Slf4j
@Service
public class MaterialReturnServiceImpl implements MaterialReturnService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 字典类型：1退料类型(金蝶退料单必需, FReturnType枚举值) 2退料原因(业务描述, 审核人审核通过时选择) */
    private static final String REASON_TYPE_RETURN_TYPE = "1";
    private static final String REASON_TYPE_RETURN_REASON = "2";

    /** ERP退料单生成最大重试次数 */
    private static final int ERP_MAX_ATTEMPTS = 3;

    @Resource
    private KingdeeService kingdeeService;
    @Resource
    private WmsService wmsService;
    @Resource
    private MaterialMessageService messageService;
    @Resource
    private MaterialProperties materialProperties;
    @Resource
    private DbMaterialCallMapper callMapper;
    @Resource
    private DbMaterialCallItemMapper itemMapper;
    @Resource
    private DbMaterialCallBatchMapper batchMapper;
    @Resource
    private DbMaterialReasonMapper reasonMapper;

    // ==================================================================
    // 接口1：获取生产订单信息
    // ==================================================================

    @Override
    public OrderInfoDTO getOrder(String orderCode, String username) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            throw new IllegalArgumentException("订单号 orderCode 不能为空");
        }
        KingdeeService.MoInfo mo = kingdeeService.queryMoByBillNo(orderCode.trim());
        if (mo == null) {
            throw new IllegalArgumentException("生产订单不存在或未审核：" + orderCode);
        }
        if (!materialProperties.allowMoStatusSet().contains(mo.getStatus())) {
            throw new IllegalArgumentException("订单状态为[" + moStatusText(mo.getStatus())
                    + "]，仅下达/开工状态订单允许补料");
        }
        OrderInfoDTO dto = new OrderInfoDTO();
        dto.setOrderCode(mo.getBillNo());
        dto.setErpOrderCode(mo.getBillNo());
        dto.setProductCode(mo.getProductCode());
        dto.setProductName(mo.getProductName());
        dto.setPlanQty(scale2(mo.getQty()));
        dto.setProducedQty(scale2(mo.getRptFinishQty()));
        dto.setOrderStatus(mapOrderStatus(mo.getStatus()));
        dto.setOrderStatusText(moStatusText(mo.getStatus()));
        dto.setPlanEndTime(datePart(mo.getPlanFinishDate()));
        return dto;
    }

    // ==================================================================
    // 接口2：获取订单用料清单
    // ==================================================================

    @Override
    public List<OrderMaterialsDTO> getOrderMaterials(List<String> orderCodes, String username) {
        List<String> orders = distinctTrimmed(orderCodes);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("订单号 orderCodes 不能为空");
        }
        // 订单信息（产品名称）
        Map<String, KingdeeService.MoInfo> moMap = new LinkedHashMap<>();
        for (String code : orders) {
            KingdeeService.MoInfo mo = kingdeeService.queryMoByBillNo(code);
            if (mo != null) moMap.put(code, mo);
        }
        if (moMap.isEmpty()) {
            throw new IllegalArgumentException("生产订单不存在或未审核：" + String.join(",", orders));
        }

        // 用料清单
        List<KingdeeService.PpbomRow> ppbomRows = kingdeeService.queryPpbom(new ArrayList<>(moMap.keySet()));

        // 即时库存（按物料汇总）
        Set<String> materialCodes = new LinkedHashSet<>();
        for (KingdeeService.PpbomRow row : ppbomRows) {
            if (row.getMaterialCode() != null) materialCodes.add(row.getMaterialCode());
        }
        Map<String, BigDecimal> stockMap = queryStockSum(new ArrayList<>(materialCodes));

        // 按订单分组
        Map<String, List<KingdeeService.PpbomRow>> byOrder = ppbomRows.stream()
                .filter(r -> r.getMoBillNo() != null)
                .collect(Collectors.groupingBy(KingdeeService.PpbomRow::getMoBillNo, LinkedHashMap::new, Collectors.toList()));

        List<OrderMaterialsDTO> result = new ArrayList<>();
        for (String code : moMap.keySet()) {
            OrderMaterialsDTO dto = new OrderMaterialsDTO();
            dto.setOrderCode(code);
            dto.setProductName(moMap.get(code).getProductName());
            List<OrderMaterialsDTO.MaterialLineDTO> lines = new ArrayList<>();
            for (KingdeeService.PpbomRow row : byOrder.getOrDefault(code, Collections.emptyList())) {
                OrderMaterialsDTO.MaterialLineDTO line = new OrderMaterialsDTO.MaterialLineDTO();
                line.setMaterialCode(row.getMaterialCode());
                line.setMaterialName(row.getMaterialName());
                line.setSpec(row.getModel());
                line.setUnit(row.getUnitName());
                BigDecimal should = nz(row.getNeedQty());
                BigDecimal issued = nz(row.getPickedQty());
                line.setShouldQty(scale2(should));
                line.setIssuedQty(scale2(issued));
                line.setDiffQty(scale2(should.subtract(issued)));
                line.setStockQty(scale2(stockMap.getOrDefault(row.getMaterialCode(), BigDecimal.ZERO)));
                lines.add(line);
            }
            dto.setMaterials(lines);
            result.add(dto);
        }
        return result;
    }

    // ==================================================================
    // 接口3：原因字典（reasonType=1退料类型 / reasonType=2退料原因）
    // ==================================================================

    @Override
    public List<Map<String, Object>> getReasons(String reasonType) {
        String type = (reasonType == null || reasonType.trim().isEmpty())
                ? REASON_TYPE_RETURN_TYPE : reasonType.trim();
        List<DbMaterialReason> list = reasonMapper.selectByType(type);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DbMaterialReason r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getReasonId());
            m.put("name", r.getName());
            result.add(m);
        }
        return result;
    }

    // ==================================================================
    // 接口4：质检员列表
    // ==================================================================

    @Override
    public List<QcStaffDTO> getQcStaffList(String username) {
        List<QcStaffDTO> list = reasonMapper.selectAllStaffList();
        return list == null ? Collections.emptyList() : list;
    }

    // ==================================================================
    // 接口5：提交补退料申请
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialCallOpResultDTO submit(MaterialSubmitRequest request, String username) {
        List<String> orderCodes = distinctTrimmed(request.getOrderList().stream()
                .map(MaterialSubmitRequest.OrderGroup::getOrderCode).collect(Collectors.toList()));

        // ===== 服务端二次校验：订单状态 + 可退数量 =====
        Map<String, KingdeeService.MoInfo> moMap = new LinkedHashMap<>();
        for (String code : orderCodes) {
            KingdeeService.MoInfo mo = kingdeeService.queryMoByBillNo(code);
            if (mo == null) {
                throw new IllegalArgumentException("生产订单不存在或未审核：" + code);
            }
            if (!materialProperties.allowMoStatusSet().contains(mo.getStatus())) {
                throw new IllegalArgumentException("订单[" + code + "]状态为[" + moStatusText(mo.getStatus())
                        + "]，仅下达/开工状态订单允许补料");
            }
            moMap.put(code, mo);
        }

        List<KingdeeService.PpbomRow> ppbomRows = kingdeeService.queryPpbom(orderCodes);
        Map<String, KingdeeService.PpbomRow> ppbomMap = new HashMap<>();
        for (KingdeeService.PpbomRow row : ppbomRows) {
            ppbomMap.put(ppbomKey(row.getMoBillNo(), row.getMaterialCode()), row);
        }
        for (MaterialSubmitRequest.OrderGroup group : request.getOrderList()) {
            for (MaterialSubmitRequest.MaterialLine line : group.getMaterials()) {
                KingdeeService.PpbomRow row = ppbomMap.get(ppbomKey(group.getOrderCode(), line.getMaterialCode()));
                if (row == null) {
                    throw new IllegalArgumentException("订单[" + group.getOrderCode() + "]的用料清单不存在物料["
                            + line.getMaterialCode() + "]，不允许退料");
                }
                BigDecimal diff = nz(row.getPickedQty());
                if (line.getQty().compareTo(diff) > 0) {
                    throw new IllegalArgumentException("订单[" + group.getOrderCode() + "]物料["
                            + line.getMaterialCode() + "]可退数量为" + scale2(diff)
                            + "，申请数量" + scale2(line.getQty()) + "超出");
                }
            }
        }

        // ===== 服务端二次校验：退料类型必须存在于 reasonType=1 字典 =====
        DbMaterialReason returnTypeDict = reasonMapper.selectByIdAndType(request.getReturnTypeId(),
                REASON_TYPE_RETURN_TYPE);
        if (returnTypeDict == null) {
            throw new IllegalArgumentException("退料类型不合法：" + request.getReturnTypeId());
        }

        // ===== 生成申请单 =====
        LocalDateTime now = LocalDateTime.now();
        DbMaterialCall call = new DbMaterialCall();
        call.setCallNo("TL" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        call.setApplicantId(request.getApplicantStaffId());
        call.setApplicantCode(username);
        call.setApplicantName(request.getApplicantName());
        call.setApplicantDept(request.getApplicantDept());
        call.setQcStaffId(request.getQcStaffId());
        call.setQcStaffCode(request.getQcStaffCode());
        call.setQcStaffName(request.getQcStaffName());
        // 2026-09-22变更：退料原因(REASON_ID/REASON_TEXT)改由质检审核人审核通过时填写，
        // 提交时即使前端仍传 reasonId/reasonText 也忽略，申请单落库为空
        call.setReasonId(null);
        call.setReasonText(null);
        call.setReturnType(request.getReturnTypeId());
        call.setReturnTypeName(request.getReturnTypeName());
        call.setStatus(DbMaterialCall.STATUS_PENDING_AUDIT);
        call.setWmsEnabled(0);
        call.setCreateTime(now);
        call.setUpdateTime(now);
        callMapper.insert(call);

        for (MaterialSubmitRequest.OrderGroup group : request.getOrderList()) {
            for (MaterialSubmitRequest.MaterialLine line : group.getMaterials()) {
                DbMaterialCallItem item = new DbMaterialCallItem();
                item.setCallId(call.getId());
                item.setOrderCode(group.getOrderCode());
                item.setMaterialCode(line.getMaterialCode());
                item.setMaterialName(line.getMaterialName());
                item.setSpec(line.getSpec());
                item.setUnit(line.getUnit());
                item.setQty(line.getQty());
                item.setCreateTime(now);
                itemMapper.insert(item);
            }
        }

        // ===== 推送质检员（M8）=====
        int itemCount = request.getOrderList().stream().mapToInt(g -> g.getMaterials().size()).sum();
        messageService.push("submit", request.getQcStaffName(),
                "新的补退料申请 " + call.getCallNo() + "\n\n申请人：" + request.getApplicantName()
                        + "\n订单：" + String.join(",", orderCodes)
                        + "\n物料：" + itemCount + "项\n请前往质检审核页面处理");

        MaterialCallOpResultDTO dto = new MaterialCallOpResultDTO();
        dto.setId(call.getId());
        dto.setCallNo(call.getCallNo());
        dto.setStatus(call.getStatus());
        return dto;
    }

    // ==================================================================
    // 接口6：我的申请列表
    // ==================================================================

    @Override
    public List<MaterialApplicationDTO> getMyApplications(String username, String status,
                                                           String startTime, String endTime) {
        List<Integer> statuses = parseStatuses(status);
        LocalDateTime st = parseStartTime(startTime);
        LocalDateTime en = parseEndTime(endTime);
        List<DbMaterialCall> calls = callMapper.selectMyApplications(
                username == null || username.trim().isEmpty() ? null : username.trim(),
                statuses, st, en);
        if (calls.isEmpty()) return Collections.emptyList();

        // 批量加载明细
        List<String> callIds = calls.stream().map(DbMaterialCall::getId).collect(Collectors.toList());
        Map<String, List<DbMaterialCallItem>> itemMap = itemMapper
                .selectList(new QueryWrapper<DbMaterialCallItem>().in("CALL_ID", callIds))
                .stream().collect(Collectors.groupingBy(DbMaterialCallItem::getCallId));

        return calls.stream().map(c -> toApplicationDTO(c,
                itemMap.getOrDefault(c.getId(), Collections.emptyList()), null)).collect(Collectors.toList());
    }

    // ==================================================================
    // 接口7：申请单详情
    // ==================================================================

    @Override
    public MaterialApplicationDTO getApplicationDetail(String id, String username) {
        DbMaterialCall call = mustGetCall(id);
        List<DbMaterialCallItem> items = itemMapper.selectByCallId(id);
        List<DbMaterialCallBatch> batches = batchMapper.selectByCallId(id);
        return toApplicationDTO(call, items, batches);
    }

    // ==================================================================
    // 接口8：质检待审列表
    // ==================================================================

    @Override
    public List<AuditListItemDTO> getAuditList(String username) {
        String qcStaff = (username == null || username.trim().isEmpty()) ? null : username.trim();
        List<DbMaterialCall> calls = callMapper.selectAuditList(qcStaff);
        /***if ((calls == null || calls.isEmpty()) && qcStaff != null) {
            // 名下无待审时回退共享待审队列，避免工号/ID口径不一致导致漏单
            calls = callMapper.selectAuditList(null);
        }***/
        if (calls == null || calls.isEmpty()) return Collections.emptyList();

        List<String> callIds = calls.stream().map(DbMaterialCall::getId).collect(Collectors.toList());
        Map<String, List<DbMaterialCallItem>> itemMap = itemMapper
                .selectList(new QueryWrapper<DbMaterialCallItem>().in("CALL_ID", callIds))
                .stream().collect(Collectors.groupingBy(DbMaterialCallItem::getCallId));

        // 订单 → 产品名称（缓存，避免重复查询金蝶）
        Map<String, String> productNameCache = new HashMap<>();
        List<AuditListItemDTO> result = new ArrayList<>();
        for (DbMaterialCall call : calls) {
            AuditListItemDTO dto = new AuditListItemDTO();
            dto.setId(call.getId());
            dto.setCallNo(call.getCallNo());
            dto.setCreateTime(fmt(call.getCreateTime()));
            dto.setApplicantName(call.getApplicantName());
            dto.setApplicantDept(call.getApplicantDept());
            dto.setReturnTypeId(call.getReturnType());
            dto.setReturnTypeText(call.getReturnTypeName());
            dto.setReasonText(call.getReasonText());

            Map<String, List<DbMaterialCallItem>> byOrder = itemMap
                    .getOrDefault(call.getId(), Collections.emptyList())
                    .stream().collect(Collectors.groupingBy(DbMaterialCallItem::getOrderCode,
                            LinkedHashMap::new, Collectors.toList()));
            List<AuditListItemDTO.OrderDTO> orderList = new ArrayList<>();
            for (Map.Entry<String, List<DbMaterialCallItem>> entry : byOrder.entrySet()) {
                AuditListItemDTO.OrderDTO od = new AuditListItemDTO.OrderDTO();
                od.setOrderCode(entry.getKey());
                od.setProductName(productNameCache.computeIfAbsent(entry.getKey(), code -> {
                    KingdeeService.MoInfo mo = kingdeeService.queryMoByBillNo(code);
                    return mo == null ? "" : mo.getProductName();
                }));
                List<AuditListItemDTO.MaterialDTO> materials = new ArrayList<>();
                for (DbMaterialCallItem it : entry.getValue()) {
                    AuditListItemDTO.MaterialDTO md = new AuditListItemDTO.MaterialDTO();
                    md.setMaterialCode(it.getMaterialCode());
                    md.setMaterialName(it.getMaterialName());
                    md.setSpec(it.getSpec());
                    md.setUnit(it.getUnit());
                    md.setQty(scale2(it.getQty()));
                    materials.add(md);
                }
                od.setMaterials(materials);
                orderList.add(od);
            }
            dto.setOrderList(orderList);
            result.add(dto);
        }
        return result;
    }

    // ==================================================================
    // 接口9：质检审核（触发 FIFO匹配 + ERP + WMS 流水线）
    // ==================================================================

    @Override
    public MaterialResult<?> audit(MaterialAuditRequest request, String username) {
        DbMaterialCall call = mustGetCall(request.getId());
        if (!Objects.equals(call.getStatus(), DbMaterialCall.STATUS_PENDING_AUDIT)) {
            throw new IllegalArgumentException("申请单当前状态为[" + statusText(call.getStatus())
                    + "]，不允许审核");
        }
        String auditBy = request.getAuditStaffName() != null && !request.getAuditStaffName().isEmpty()
                ? request.getAuditStaffName() : username;

        // ===== 驳回 =====
        if (Objects.equals(request.getAuditResult(), 2)) {
            if (request.getRejectReason() == null || request.getRejectReason().trim().isEmpty()) {
                throw new IllegalArgumentException("驳回时必须填写驳回原因");
            }
            int affected = updateStatus(call.getId(), DbMaterialCall.STATUS_PENDING_AUDIT,
                    DbMaterialCall.STATUS_REJECTED, uw -> uw
                            .set(DbMaterialCall::getRejectReason, request.getRejectReason().trim())
                            .set(DbMaterialCall::getAuditBy, auditBy)
                            .set(DbMaterialCall::getAuditTime, LocalDateTime.now()));
            if (affected == 0) throw new IllegalArgumentException("申请单已被处理，请刷新后重试");
            messageService.push("audit-reject", call.getApplicantName(),
                    "补退料申请 " + call.getCallNo() + " 已驳回\n\n驳回原因：" + request.getRejectReason().trim());
            return MaterialResult.ok(opResult(call.getId(), call.getCallNo(),
                    DbMaterialCall.STATUS_REJECTED, null, null, null));
        }

        // ===== 通过 =====
        if (!Objects.equals(request.getAuditResult(), 1)) {
            throw new IllegalArgumentException("审核结果不合法（1通过 2驳回）");
        }
        // 退料类型由前端随审核请求回传(取自接口8列表项)：必填且必须存在于 reasonType=1 字典；
        // 历史单据(RETURN_TYPE为空且前端未回传)在此被拦截，由申请人重新发起
        String returnType = request.getReturnTypeId();
        if (returnType == null) {
            throw new IllegalArgumentException("退料类型必填");
        }
        DbMaterialReason returnTypeDict = reasonMapper.selectByIdAndType(returnType, REASON_TYPE_RETURN_TYPE);
        if (returnTypeDict == null) {
            throw new IllegalArgumentException("退料类型不合法：" + returnType);
        }
        String returnTypeName = returnTypeDict.getName();

        // 2026-09-22变更：退料原因改由质检审核人在审核通过时选择(reasonType=2字典)，必填；
        // 校验通过后回写申请单 REASON_ID/REASON_TEXT，M6生成金蝶退料单时写入FReturnReason
        String reasonId = request.getReasonId() == null ? null : request.getReasonId().trim();
        String reasonText = request.getReasonText() == null ? null : request.getReasonText().trim();
        if (reasonId == null || reasonId.isEmpty() || reasonText == null || reasonText.isEmpty()) {
            // 文档约定：缺失返回 status=-1, msg=退料原因为必填项
            return MaterialResult.error(-1, "退料原因为必填项");
        }
        DbMaterialReason reasonDict = reasonMapper.selectByIdAndType(reasonId, REASON_TYPE_RETURN_REASON);
        if (reasonDict == null) {
            return MaterialResult.error(-1, "退料原因不合法：" + reasonId);
        }
        // 名称以审核请求回传为准(字典名校验兜底)
        if (reasonDict.getName() != null && !reasonDict.getName().isEmpty()) {
            reasonText = reasonDict.getName();
        }
        final String finalReasonText = reasonText;
        boolean force = Objects.equals(request.getForceFlag(), 1);

        // 10 → 20 批次匹配中（RETURN_TYPE/REASON_* 均以审核回传值为准覆盖写入）
        int affected = updateStatus(call.getId(), DbMaterialCall.STATUS_PENDING_AUDIT,
                DbMaterialCall.STATUS_MATCHING, uw -> uw
                        .set(DbMaterialCall::getReturnType, returnType)
                        .set(DbMaterialCall::getReturnTypeName, returnTypeName)
                        .set(DbMaterialCall::getReasonId, reasonId)
                        .set(DbMaterialCall::getReasonText, finalReasonText)
                        .set(DbMaterialCall::getAuditBy, auditBy)
                        .set(DbMaterialCall::getAuditTime, LocalDateTime.now())
                        .set(DbMaterialCall::getErrorMsg, null));
        if (affected == 0) throw new IllegalArgumentException("申请单已被处理，请刷新后重试");

        // ===== M3 FIFO 批次匹配 =====
        List<DbMaterialCallItem> items = itemMapper.selectByCallId(call.getId());
        List<DbMaterialCallBatch> allocations;
        try {
            allocations = matchFifo(call, items, force);
        } catch (PartialMatchException e) {
            // 库存不足且未强制：回退待审核，返回部分匹配
            updateStatus(call.getId(), DbMaterialCall.STATUS_MATCHING,
                    DbMaterialCall.STATUS_PENDING_AUDIT, null);
            MaterialCallOpResultDTO.PartialMatchDTO pm = new MaterialCallOpResultDTO.PartialMatchDTO();
            pm.setRequestQty(scale2(e.getRequestQty()));
            pm.setMatchQty(scale2(e.getMatchQty()));
            log.info("[补退料] 申请{}部分匹配：申请{} 可匹配{}", call.getCallNo(), e.getRequestQty(), e.getMatchQty());
            return MaterialResult.partial(pm, "库存不足");
        }
        LocalDateTime now = LocalDateTime.now();
        for (DbMaterialCallBatch b : allocations) {
            b.setCreateTime(now);
            batchMapper.insert(b);
        }

        // ===== M6 生成金蝶退料单+补料单（均可上查生产用料清单） =====
        // 20 → 30 单据生成中
        updateStatus(call.getId(), DbMaterialCall.STATUS_MATCHING, DbMaterialCall.STATUS_ERP_CREATING, null);
        call = mustGetCall(call.getId());
        String[] erpBillNos;
        try {
            erpBillNos = createErpOrdersWithRetry(call, items, allocations);
        } catch (ErpCreateFailedException e) {
            log.error("[补退料] 申请{}退料单/补料单生成失败: {}", call.getCallNo(), e.getMessage(), e);
            String errMsg = truncateDb(e.getMessage(), 500);
            callMapper.update(null, new LambdaUpdateWrapper<DbMaterialCall>()
                    .eq(DbMaterialCall::getId, call.getId())
                    .eq(DbMaterialCall::getStatus, DbMaterialCall.STATUS_ERP_CREATING)
                    .set(DbMaterialCall::getStatus, DbMaterialCall.STATUS_ERP_ERROR)
                    .set(DbMaterialCall::getErrorMsg, errMsg)
                    // Save成功但Submit/Audit失败的单号，重试时续传避免重复建单
                    .set(e.getBillNo() != null && !e.getBillNo().isEmpty(),
                            DbMaterialCall::getErpOrderNo, e.getBillNo())
                    .set(e.getFeedBillNo() != null && !e.getFeedBillNo().isEmpty(),
                            DbMaterialCall::getErpReplenishOrderNo, e.getFeedBillNo()));
            messageService.push("erp-error", "admin",
                    "补退料申请 " + call.getCallNo() + " 退料单/补料单生成异常：" + errMsg);
            return MaterialResult.error("退料单/补料单生成失败：" + errMsg);
        } catch (Exception e) {
            log.error("[补退料] 申请{}退料单/补料单生成失败: {}", call.getCallNo(), e.getMessage(), e);
            String errMsg = truncateDb(e.getMessage(), 500);
            callMapper.update(null, new LambdaUpdateWrapper<DbMaterialCall>()
                    .eq(DbMaterialCall::getId, call.getId())
                    .eq(DbMaterialCall::getStatus, DbMaterialCall.STATUS_ERP_CREATING)
                    .set(DbMaterialCall::getStatus, DbMaterialCall.STATUS_ERP_ERROR)
                    .set(DbMaterialCall::getErrorMsg, errMsg));
            messageService.push("erp-error", "admin",
                    "补退料申请 " + call.getCallNo() + " 退料单/补料单生成异常：" + errMsg);
            return MaterialResult.error("退料单/补料单生成失败：" + errMsg);
        }

        // 30 → 40 退料单/补料单已生成
        String erpBillNo = erpBillNos[0];
        String erpFeedBillNo = erpBillNos[1];
        updateStatus(call.getId(), DbMaterialCall.STATUS_ERP_CREATING, DbMaterialCall.STATUS_ERP_CREATED, uw ->
                uw.set(DbMaterialCall::getErpOrderNo, erpBillNo)
                        .set(DbMaterialCall::getErpReplenishOrderNo, erpFeedBillNo)
                        .set(DbMaterialCall::getErrorMsg, null));
        messageService.push("audit-pass", call.getApplicantName(),
                "补退料申请 " + call.getCallNo() + " 已审核通过\n\n金蝶退料单号：" + erpBillNo
                        + "\n金蝶补料单号：" + erpFeedBillNo);

        // ===== M7 WMS 出库申请 + 收尾 =====
        return finishWmsAndComplete(call.getId());
    }

    // ==================================================================
    // 接口10：重试生成ERP退料单
    // ==================================================================

    @Override
    public MaterialCallOpResultDTO retryErpOrder(String id, String username) {
        DbMaterialCall call = mustGetCall(id);
        if (!Objects.equals(call.getStatus(), DbMaterialCall.STATUS_ERP_ERROR)) {
            throw new IllegalArgumentException("仅退料单生成异常(31)状态允许重试，当前状态["
                    + statusText(call.getStatus()) + "]");
        }
        List<DbMaterialCallItem> items = itemMapper.selectByCallId(id);
        List<DbMaterialCallBatch> batches = batchMapper.selectByCallId(id);
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("批次匹配结果缺失，无法生成退料单");
        }

        // 31 → 30
        int affected = updateStatus(id, DbMaterialCall.STATUS_ERP_ERROR,
                DbMaterialCall.STATUS_ERP_CREATING, null);
        if (affected == 0) throw new IllegalArgumentException("申请单已被处理，请刷新后重试");

        call = mustGetCall(id);
        String[] erpBillNos;
        try {
            erpBillNos = createErpOrdersWithRetry(call, items, batches);
        } catch (ErpCreateFailedException e) {
            log.error("[补退料] 申请{}重试退料单/补料单生成失败: {}", call.getCallNo(), e.getMessage(), e);
            String errMsg = truncateDb(e.getMessage(), 500);
            callMapper.update(null, new LambdaUpdateWrapper<DbMaterialCall>()
                    .eq(DbMaterialCall::getId, id)
                    .eq(DbMaterialCall::getStatus, DbMaterialCall.STATUS_ERP_CREATING)
                    .set(DbMaterialCall::getStatus, DbMaterialCall.STATUS_ERP_ERROR)
                    .set(DbMaterialCall::getErrorMsg, errMsg)
                    .set(e.getBillNo() != null && !e.getBillNo().isEmpty(),
                            DbMaterialCall::getErpOrderNo, e.getBillNo())
                    .set(e.getFeedBillNo() != null && !e.getFeedBillNo().isEmpty(),
                            DbMaterialCall::getErpReplenishOrderNo, e.getFeedBillNo()));
            throw new IllegalArgumentException("重试生成退料单/补料单失败：" + errMsg);
        } catch (Exception e) {
            log.error("[补退料] 申请{}重试退料单/补料单生成失败: {}", call.getCallNo(), e.getMessage(), e);
            String errMsg = truncateDb(e.getMessage(), 500);
            callMapper.update(null, new LambdaUpdateWrapper<DbMaterialCall>()
                    .eq(DbMaterialCall::getId, id)
                    .eq(DbMaterialCall::getStatus, DbMaterialCall.STATUS_ERP_CREATING)
                    .set(DbMaterialCall::getStatus, DbMaterialCall.STATUS_ERP_ERROR)
                    .set(DbMaterialCall::getErrorMsg, errMsg));
            throw new IllegalArgumentException("重试生成退料单/补料单失败：" + errMsg);
        }

        String erpBillNo = erpBillNos[0];
        String erpFeedBillNo = erpBillNos[1];
        updateStatus(id, DbMaterialCall.STATUS_ERP_CREATING, DbMaterialCall.STATUS_ERP_CREATED, uw ->
                uw.set(DbMaterialCall::getErpOrderNo, erpBillNo)
                        .set(DbMaterialCall::getErpReplenishOrderNo, erpFeedBillNo)
                        .set(DbMaterialCall::getErrorMsg, null));
        messageService.push("audit-pass", call.getApplicantName(),
                "补退料申请 " + call.getCallNo() + " 退料单/补料单已生成\n\n金蝶退料单号：" + erpBillNo
                        + "\n金蝶补料单号：" + erpFeedBillNo);

        MaterialResult<MaterialCallOpResultDTO> result = finishWmsAndComplete(id);
        if (!Objects.equals(result.getStatus(), MaterialResult.SUCCESS)) {
            throw new IllegalArgumentException(result.getMsg());
        }
        return result.getData();
    }

    // ==================================================================
    // 接口11：重试生成WMS出库申请
    // ==================================================================

    @Override
    public MaterialCallOpResultDTO retryWmsOrder(String id, String username) {
        DbMaterialCall call = mustGetCall(id);
        if (!Objects.equals(call.getStatus(), DbMaterialCall.STATUS_WMS_ERROR)) {
            throw new IllegalArgumentException("仅WMS申请异常(41)状态允许重试，当前状态["
                    + statusText(call.getStatus()) + "]");
        }
        MaterialResult<MaterialCallOpResultDTO> result = finishWmsAndComplete(id);
        if (!Objects.equals(result.getStatus(), MaterialResult.SUCCESS)) {
            throw new IllegalArgumentException(result.getMsg());
        }
        return result.getData();
    }

    // ==================================================================
    // 运维工具：按申请单号(TL...)强制重新推送金蝶生产退料单
    // ==================================================================

    /**
     * 强制重新推送指定申请单的金蝶生产退料单（仓库出错等场景使用）。
     * <p>不复用、不续传 DB_MATERIAL_CALL.ERP_ORDER_NO 中的旧单号，全部按最新生产领料来源
     * （queryPickStock 实际发料仓库/库存组织）重新构建分录并调用
     * {@link KingdeeService#createReturnOrder} 建单；旧的错误退料单需人工在金蝶中删除/作废。
     * 成功后新单号回写 ERP_ORDER_NO，申请单状态不变，WMS流程不重复触发。</p>
     *
     * @param callNo 补退料申请单号，如 TL20260921140022102
     * @return 新生成的金蝶退料单号（跨组织拆单时为逗号分隔的多个单号）
     */
    public String repushReturnOrder(String callNo) {
        if (callNo == null || callNo.trim().isEmpty()) {
            throw new IllegalArgumentException("申请单号 callNo 不能为空");
        }
        String no = callNo.trim();
        DbMaterialCall call = callMapper.selectOne(
                new QueryWrapper<DbMaterialCall>().eq("CALL_NO", no));
        if (call == null) {
            throw new IllegalArgumentException("补退料申请单不存在: " + no);
        }
        List<DbMaterialCallItem> items = itemMapper.selectByCallId(call.getId());
        List<DbMaterialCallBatch> batches = batchMapper.selectByCallId(call.getId());
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("申请单" + no + "无物料明细，无法重推退料单");
        }
        if (batches == null || batches.isEmpty()) {
            throw new IllegalStateException("申请单" + no + "无批次匹配结果，无法重推退料单");
        }

        String oldBillNos = call.getErpOrderNo();
        log.warn("[补退料-重推] 申请{} 开始强制重推退料单，旧金蝶退料单号={}（重推后旧单需人工在金蝶删除/作废）",
                no, oldBillNos);

        // createErpOrdersWithRetry 以 call.erpOrderNo 作为续传清单；内存中清空即强制全部重新建单，
        // 不会续传仓库错误的旧草稿（仅内存对象生效，DB回写在成功/部分失败时处理）
        call.setErpOrderNo(null);
        call.setErpReplenishOrderNo(null);

        String[] erpBillNos;
        try {
            erpBillNos = createErpOrdersWithRetry(call, items, batches);
        } catch (ErpCreateFailedException e) {
            // 部分库存组织分组已Save成功：先把新单号落库，避免再次执行时重复建单（可据此续传）
            if (e.getBillNo() != null && !e.getBillNo().isEmpty()) {
                callMapper.update(null, new LambdaUpdateWrapper<DbMaterialCall>()
                        .eq(DbMaterialCall::getId, call.getId())
                        .set(DbMaterialCall::getErpOrderNo, e.getBillNo())
                        .set(DbMaterialCall::getUpdateTime, LocalDateTime.now()));
                log.error("[补退料-重推] 申请{} 重推部分失败，已Save的新退料单号{}已回写ERP_ORDER_NO",
                        no, e.getBillNo());
            }
            throw e;
        }

        String newBillNos = erpBillNos[0];
        callMapper.update(null, new LambdaUpdateWrapper<DbMaterialCall>()
                .eq(DbMaterialCall::getId, call.getId())
                .set(DbMaterialCall::getErpOrderNo, newBillNos)
                .set(DbMaterialCall::getUpdateTime, LocalDateTime.now()));
        log.warn("[补退料-重推] 申请{} 重推完成：旧单号={} → 新退料单号={}", no, oldBillNos, newBillNos);
        return newBillNos;
    }

    /**
     * 运维入口（IDE 中直接 Run 本类的 main）：按申请单号强制重新推送生产退料单。
     * <p>默认重推 {@value #DEFAULT_REPUSH_CALL_NO}；也可用启动参数覆盖，例如：
     * java ...MaterialReturnServiceImpl TL20260921140022102</p>
     */
    public static void main(String[] args) {
        
        String callNo = "TL20260916084822494";
        //来源安灯补退料申请单: TL20260916084822494
        SpringApplication app = new SpringApplication(TaskServiceApplication.class);
        // 工具模式：不启动Web容器（不占用8081端口），不注册/拉取Nacos配置
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.cloud.nacos.discovery.enabled", "false",
                "spring.cloud.nacos.config.enabled", "false",
                // 重推退料单不依赖Redis；本地未启动Redis时排除Redisson自动装配，避免启动期强连6379失败
                "spring.autoconfigure.exclude", "org.redisson.spring.starter.RedissonAutoConfigurationV2"));
        ConfigurableApplicationContext ctx = app.run(new String[0]);

        int exitCode = 1;
        try {
            String newBillNos = ctx.getBean(MaterialReturnServiceImpl.class).repushReturnOrder(callNo);
            System.out.println("========== 生产退料单重推成功 ==========");
            System.out.println("申请单号: " + callNo);
            System.out.println("新金蝶退料单号: " + newBillNos);
            System.out.println("注意：旧的错误退料单请人工在金蝶中删除/作废；"
                    + "当前 createReturnOrder 仅执行Save，提交/审核按金蝶现有流程处理。");
            exitCode = 0;
        } catch (Exception e) {
            System.err.println("========== 生产退料单重推失败 ==========");
            System.err.println("申请单号: " + callNo);
            e.printStackTrace();
        } finally {
            SpringApplication.exit(ctx);
            System.exit(exitCode);
        }
    }

    // ==================================================================
    // M3 FIFO 批次匹配
    // ==================================================================

    /** 库存不足部分匹配信号 */
    private static class PartialMatchException extends RuntimeException {
        private final BigDecimal requestQty;
        private final BigDecimal matchQty;

        PartialMatchException(BigDecimal requestQty, BigDecimal matchQty) {
            super("库存不足");
            this.requestQty = requestQty;
            this.matchQty = matchQty;
        }

        BigDecimal getRequestQty() { return requestQty; }
        BigDecimal getMatchQty() { return matchQty; }
    }

    /**
     * FIFO 匹配：严格按批次入库时间升序分配（STK_Inventory FUpdateTime asc），
     * 扣除进行中申请的预占；单批不足自动拆批；总可用不足且未强制时抛部分匹配。
     */
    private List<DbMaterialCallBatch> matchFifo(DbMaterialCall call, List<DbMaterialCallItem> items,
                                                boolean force) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("申请单物料明细为空");
        }
        List<String> materialCodes = items.stream()
                .map(DbMaterialCallItem::getMaterialCode).distinct().collect(Collectors.toList());

        // 金蝶即时库存（已按FUpdateTime asc 排序 = FIFO）
        List<KingdeeService.StockRow> stockRows = kingdeeService.queryStock(materialCodes);

        // 进行中申请的预占（物料+批次）
        Map<String, BigDecimal> reservedMap = new HashMap<>();
        for (var r : batchMapper.sumActiveReservations()) {
            reservedMap.put(r.getMaterialCode() + "|" + r.getBatchNo(), nz(r.getQty()));
        }

        // 可用量池：物料 → 批次池（保持FIFO顺序）
        Map<String, List<StockPool>> pools = new LinkedHashMap<>();
        for (KingdeeService.StockRow row : stockRows) {
            BigDecimal reserved = reservedMap.getOrDefault(
                    row.getMaterialCode() + "|" + row.getLotNumber(), BigDecimal.ZERO);
            BigDecimal available = nz(row.getBaseQty()).subtract(reserved);
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;
            pools.computeIfAbsent(row.getMaterialCode(), k -> new ArrayList<>())
                    .add(new StockPool(row, available));
        }

        // 按物料汇总申请量（同一物料可能存在多行明细）
        Map<String, BigDecimal> requestByMaterial = new LinkedHashMap<>();
        for (DbMaterialCallItem item : items) {
            requestByMaterial.merge(item.getMaterialCode(), nz(item.getQty()), BigDecimal::add);
        }
        // 逐物料校验可用量：任一物料不足且未强制时抛部分匹配（总量充足不能掩盖单一物料缺口）
        BigDecimal requestTotal = BigDecimal.ZERO;
        BigDecimal matchTotal = BigDecimal.ZERO;
        boolean shortage = false;
        for (Map.Entry<String, BigDecimal> en : requestByMaterial.entrySet()) {
            BigDecimal materialAvail = pools.getOrDefault(en.getKey(), Collections.emptyList())
                    .stream().map(p -> p.available).reduce(BigDecimal.ZERO, BigDecimal::add);
            requestTotal = requestTotal.add(en.getValue());
            matchTotal = matchTotal.add(materialAvail.min(en.getValue()));
            if (materialAvail.compareTo(en.getValue()) < 0) {
                shortage = true;
            }
        }
        if (shortage && !force) {
            throw new PartialMatchException(requestTotal, matchTotal);
        }

        // 逐明细按FIFO分配
        List<DbMaterialCallBatch> result = new ArrayList<>();
        for (DbMaterialCallItem item : items) {
            BigDecimal remaining = nz(item.getQty());
            List<StockPool> materialPools = pools.getOrDefault(item.getMaterialCode(), Collections.emptyList());
            for (StockPool pool : materialPools) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                if (pool.available.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal take = pool.available.min(remaining);
                pool.available = pool.available.subtract(take);
                remaining = remaining.subtract(take);

                DbMaterialCallBatch b = new DbMaterialCallBatch();
                b.setCallId(call.getId());
                b.setCallItemId(item.getId());
                b.setMaterialCode(item.getMaterialCode());
                b.setBatchNo(pool.row.getLotNumber());
                b.setWarehouse(pool.row.getStockName());
                b.setWarehouseCode(pool.row.getStockNumber());
                b.setStockOrg(pool.row.getStockOrgNumber());
                b.setLocationId(pool.row.getLocationId());
                b.setQty(scale2(take));
                b.setWmsFlag(materialProperties.getWms()
                        .isWmsWarehouse(pool.row.getStockNumber()) ? 1 : 0);
                b.setReserved(1);
                result.add(b);
            }
        }
        return result;
    }

    /** FIFO 可用量池 */
    private static class StockPool {
        final KingdeeService.StockRow row;
        BigDecimal available;

        StockPool(KingdeeService.StockRow row, BigDecimal available) {
            this.row = row;
            this.available = available;
        }
    }

    // ==================================================================
    // M6 金蝶退料单+补料单生成（Save → Submit → Audit，最多重试3次）
    // ==================================================================

    /** ERP单据生成最终失败（可能携带已保存但未提交/审核的单号，供下次重试续传） */
    private static class ErpCreateFailedException extends RuntimeException {
        private final transient String billNo;      // 退料单号（续传或已成功）
        private final transient String feedBillNo;  // 补料单号（续传）

        ErpCreateFailedException(String billNo, String feedBillNo, String message) {
            super(message);
            this.billNo = billNo;
            this.feedBillNo = feedBillNo;
        }

        String getBillNo() {
            return billNo;
        }

        String getFeedBillNo() {
            return feedBillNo;
        }
    }

    /**
     * 生成退料单+补料单（带重试）。重试幂等：已生成且已审核的单据直接跳过，
     * Save成功但Submit/Audit失败的单据按续传处理，避免重复建单。
     * <p>2026-09-22变更：退料单按领料来源库存组织拆单，erpOrderNo 可能为逗号分隔的多个金蝶单号。</p>
     * @return [退料单号(多个逗号分隔), 补料单号]
     */
    private String[] createErpOrdersWithRetry(DbMaterialCall call, List<DbMaterialCallItem> items,
                                              List<DbMaterialCallBatch> batches) {
        String resumeRetBillNos = call.getErpOrderNo();           // 已生成退料单号(可能多个, 逗号分隔)
        String resumeFeedBillNo = call.getErpReplenishOrderNo();  // 之前Save成功但未完成审核的补料单号

        Exception lastError = null;
        for (int attempt = 1; attempt <= ERP_MAX_ATTEMPTS; attempt++) {
            try {
                return buildAndCreateErpOrders(call, items, batches, resumeRetBillNos, resumeFeedBillNo);
            } catch (KingdeeService.BillStageException e) {
                if (e.getBillNo() != null && !e.getBillNo().isEmpty()) {
                    if ("PRD_FeedMtrl".equals(e.getFormId())) {
                        resumeFeedBillNo = e.getBillNo();
                    } else {
                        // 退料单按组织拆单，已Save成功的分组单号合并到续传清单，避免重复建单
                        resumeRetBillNos = mergeCsv(resumeRetBillNos, e.getBillNo());
                    }
                }
                lastError = e;
                log.warn("[补退料] 申请{}单据生成第{}次失败[{}]: {}", call.getCallNo(), attempt,
                        e.getFormId(), e.getMessage());
            } catch (Exception e) {
                lastError = e;
                log.warn("[补退料] 申请{}单据生成第{}次失败: {}", call.getCallNo(), attempt, e.getMessage());
            }
            if (attempt < ERP_MAX_ATTEMPTS) {
                sleepQuietly(1000L * attempt);
            }
        }
        throw new ErpCreateFailedException(resumeRetBillNos, resumeFeedBillNo,
                "金蝶退料单/补料单生成重试" + ERP_MAX_ATTEMPTS + "次均失败: "
                        + (lastError == null ? "未知错误" : lastError.getMessage()));
    }

    /** 单号非空且金蝶审核状态为C（已审核）时返回true；查询失败视为未完成，走续传由金蝶校验 */
    private boolean isBillAudited(String formId, String billNo) {
        if (billNo == null || billNo.isEmpty()) return false;
        return "C".equals(kingdeeService.viewBillDocumentStatus(formId, billNo));
    }

    /** 退料单分组：同一库存组织(+生产组织)一张退料单，跨组织发料强制拆单 */
    private static class ReturnOrderGroup {
        final String stockOrgNumber;
        final String prdOrgNumber;
        final List<KingdeeService.ReturnOrderEntry> entries = new ArrayList<>();

        ReturnOrderGroup(String stockOrgNumber, String prdOrgNumber) {
            this.stockOrgNumber = stockOrgNumber;
            this.prdOrgNumber = prdOrgNumber;
        }
    }

    private String[] buildAndCreateErpOrders(DbMaterialCall call, List<DbMaterialCallItem> items,
                                             List<DbMaterialCallBatch> batches,
                                             String resumeRetBillNos, String resumeFeedBillNo) {
        Map<String, DbMaterialCallItem> itemMap = items.stream()
                .collect(Collectors.toMap(DbMaterialCallItem::getId, i -> i, (a, b) -> a));

        // 仓位内码回填：升级前匹配的历史批次行LOCATION_ID为空，按物料+批次+仓库从即时库存补齐（启用仓位管理的仓库Save必录）
        backfillLocationIds(call, batches);

        // 订单分录信息（entryId/seq/产品/车间）
        Set<String> orderCodes = batches.stream()
                .map(b -> itemMap.get(b.getCallItemId()).getOrderCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, KingdeeService.MoEntryDetail> moEntryMap = new HashMap<>();
        for (String code : orderCodes) {
            KingdeeService.MoEntryDetail detail = kingdeeService.viewMoEntry(code);
            if (detail.getEntryId() == null || detail.getProductMaterialNumber() == null
                    || detail.getWorkshopNumber() == null) {
                throw new RuntimeException("生产订单[" + code + "]分录信息加载失败，无法生成退料单/补料单");
            }
            moEntryMap.put(code, detail);
        }

        // 物料单位编码 + 用料清单标识（FID/单号，上查关联所需）
        Map<String, KingdeeService.PpbomRow> ppbomMap = new HashMap<>();
        for (KingdeeService.PpbomRow row : kingdeeService.queryPpbom(new ArrayList<>(orderCodes))) {
            ppbomMap.put(ppbomKey(row.getMoBillNo(), row.getMaterialCode()), row);
        }
        // 用料清单分录详情（分录内码/行号/工序，View获取）→ 订单|物料 → 分录
        Map<String, KingdeeService.PpbomEntryDetail> ppbomEntryMap = loadPpbomEntries(ppbomMap);
        log.info("[补退料] 申请{} ppbomMap键={}, ppbomEntryMap键={}", call.getCallNo(),
                ppbomMap.keySet(), ppbomEntryMap.keySet());

        // ===== 退料仓库/库存组织按生产领料单来源还原（从哪里领料、退回哪里）=====
        Set<Long> moEntryIds = batches.stream()
                .map(b -> moEntryMap.get(itemMap.get(b.getCallItemId()).getOrderCode()).getEntryId())
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> pickMaterialCodes = batches.stream()
                .map(DbMaterialCallBatch::getMaterialCode).collect(Collectors.toCollection(LinkedHashSet::new));
        List<KingdeeService.PickStockRow> pickRows =
                kingdeeService.queryPickStock(moEntryIds, pickMaterialCodes);
        log.info("[补退料] 申请{} 查到生产领料发料记录{}条", call.getCallNo(), pickRows.size());

        // 退料原因 → 金蝶FReturnReason.FNumber：审核人选择的reasonType=2字典REASON_ID即金蝶编码，
        // 字典维护了ERP_CODE时优先取ERP_CODE（租户模板映射）
        String returnReasonCode = null;
        if (call.getReasonId() != null && !call.getReasonId().trim().isEmpty()) {
            String rid = call.getReasonId().trim();
            DbMaterialReason reasonDict = reasonMapper.selectByIdAndType(rid, REASON_TYPE_RETURN_REASON);
            if (reasonDict != null && reasonDict.getErpCode() != null && !reasonDict.getErpCode().trim().isEmpty()) {
                returnReasonCode = reasonDict.getErpCode().trim();
            } else {
                returnReasonCode = rid;
            }
        }

        // 按库存组织(+生产组织)分组退料单分录
        Map<String, ReturnOrderGroup> retGroups = new LinkedHashMap<>();
        List<KingdeeService.FeedOrderEntry> feedEntries = new ArrayList<>();
        for (DbMaterialCallBatch batch : batches) {
            DbMaterialCallItem item = itemMap.get(batch.getCallItemId());
            KingdeeService.MoEntryDetail moEntry = moEntryMap.get(item.getOrderCode());
            KingdeeService.PpbomRow ppbom = ppbomMap.get(ppbomKey(item.getOrderCode(), batch.getMaterialCode()));
            String unitNumber = ppbom == null ? null : ppbom.getUnitNumber();
            if (unitNumber == null || unitNumber.isEmpty()) {
                throw new RuntimeException("物料[" + batch.getMaterialCode() + "]单位信息缺失，无法生成退料单/补料单");
            }
            KingdeeService.PpbomEntryDetail ppbomEntry =
                    ppbomEntryMap.get(ppbomKey(item.getOrderCode(), batch.getMaterialCode()));
            log.info("[补退料] 申请{} 匹配分录 key={}, 匹配结果={}", call.getCallNo(),
                    ppbomKey(item.getOrderCode(), batch.getMaterialCode()),
                    ppbomEntry == null ? "无" : ("entryId=" + ppbomEntry.getEntryId()));
            if (ppbomEntry == null || ppbomEntry.getEntryId() == null) {
                throw new RuntimeException("物料[" + batch.getMaterialCode() + "]用料清单分录信息缺失，无法建立上查关联");
            }

            // 取该批次物料在生产领料单上的实际发料仓库/库存组织（查不到必须拦截，不得回退默认组织）
            KingdeeService.PickStockRow pick = resolvePickStock(call, pickRows,
                    moEntry.getEntryId(), item.getOrderCode(), batch.getMaterialCode(), batch.getBatchNo());

            // 退料单分录：仓库/仓位取领料来源，不再使用即时库存仓库或默认组织
            KingdeeService.ReturnOrderEntry retEntry = new KingdeeService.ReturnOrderEntry();
            retEntry.setMaterialNumber(batch.getMaterialCode());
            retEntry.setUnitNumber(unitNumber);
            retEntry.setQty(batch.getQty());
            // 退料类型取申请单RETURN_TYPE(发起人提交时选择, 审核通过时以回传值为准覆盖)
            retEntry.setReturnType(call.getReturnType());
            // 退料原因取审核人审核通过时回写的值(REASON_ID → FReturnReason.FNumber)
            retEntry.setReturnReasonCode(returnReasonCode);
            retEntry.setStockNumber(pick.getStockNumber());
            retEntry.setLotNumber(batch.getBatchNo());
            retEntry.setMoBillNo(item.getOrderCode());
            retEntry.setMoId(moEntry.getMoId());
            retEntry.setMoEntryId(moEntry.getEntryId());
            retEntry.setMoEntrySeq(moEntry.getEntrySeq());
            retEntry.setProductMaterialNumber(moEntry.getProductMaterialNumber());
            retEntry.setWorkshopNumber(moEntry.getWorkshopNumber());
            retEntry.setPpbomId(ppbomEntry.getPpbomId());
            retEntry.setPpbomBillNo(ppbomEntry.getPpbomBillNo());
            retEntry.setPpbomEntryId(ppbomEntry.getEntryId());
            retEntry.setPpbomEntrySeq(ppbomEntry.getEntrySeq());
            // 仓位优先取领料单分录实际发料仓位；领料单未启用仓位(0)时回退批次匹配仓位
            retEntry.setLocationId(pick.getStockLocId() != null && pick.getStockLocId() > 0
                    ? pick.getStockLocId() : batch.getLocationId());
            // 计划跟踪号透传用料清单FMTONO（方案A对齐，空值传空串由KingdeeService处理）
            retEntry.setMtoNo(ppbom.getMtoNo());

            String groupKey = pick.getStockOrgNumber() + "|" + moEntry.getPrdOrgNumber();
            retGroups.computeIfAbsent(groupKey,
                    k -> new ReturnOrderGroup(pick.getStockOrgNumber(), moEntry.getPrdOrgNumber()))
                    .entries.add(retEntry);

            // 补料单分录（补料单当前停用，保留组装；仓库沿用批次匹配仓库）
            KingdeeService.FeedOrderEntry feedEntry = new KingdeeService.FeedOrderEntry();
            feedEntry.setMaterialNumber(batch.getMaterialCode());
            feedEntry.setUnitNumber(unitNumber);
            feedEntry.setQty(batch.getQty());
            feedEntry.setStockNumber(batch.getWarehouseCode());
            feedEntry.setLotNumber(batch.getBatchNo());
            feedEntry.setMoBillNo(item.getOrderCode());
            feedEntry.setMoId(moEntry.getMoId());
            feedEntry.setMoEntryId(moEntry.getEntryId());
            feedEntry.setMoEntrySeq(moEntry.getEntrySeq());
            feedEntry.setProductMaterialNumber(moEntry.getProductMaterialNumber());
            feedEntry.setWorkshopNumber(moEntry.getWorkshopNumber());
            feedEntry.setPpbomId(ppbomEntry.getPpbomId());
            feedEntry.setPpbomBillNo(ppbomEntry.getPpbomBillNo());
            feedEntry.setPpbomEntryId(ppbomEntry.getEntryId());
            feedEntry.setPpbomEntrySeq(ppbomEntry.getEntrySeq());
            feedEntry.setOperId(ppbomEntry.getOperId());
            feedEntry.setLocationId(batch.getLocationId());
            feedEntry.setMtoNo(ppbom.getMtoNo());
            feedEntries.add(feedEntry);
        }

        String date = LocalDate.now().atStartOfDay().format(FMT); // yyyy-MM-dd 00:00:00，与金蝶报文格式一致
        String description = "来源安灯补退料申请单: " + call.getCallNo();

        // ===== 已生成退料单盘点：单号 → 审核状态/库存组织（续传或跳过的依据）=====
        List<String> existBills = splitCsv(resumeRetBillNos);
        Map<String, String> existBillOrg = new HashMap<>();
        Set<String> auditedBills = new HashSet<>();
        for (String billNo : existBills) {
            if ("C".equals(kingdeeService.viewBillDocumentStatus("PRD_ReturnMtrl", billNo))) {
                auditedBills.add(billNo);
            }
            existBillOrg.put(billNo, kingdeeService.viewBillStockOrg("PRD_ReturnMtrl", billNo));
        }
        Set<String> assignedBills = new HashSet<>();

        // ===== 按库存组织逐组生成退料单（跨组织拆单）=====
        List<String> retBillNos = new ArrayList<>();
        for (ReturnOrderGroup group : retGroups.values()) {
            String resumeBillNo = matchResumeBill(group, existBills, existBillOrg, auditedBills, assignedBills);
            String billNo;
            if (auditedBills.contains(resumeBillNo)) {
                // 该组织退料单已审核，跳过（幂等）
                billNo = resumeBillNo;
                log.info("[补退料] 申请{} 库存组织{}退料单{}已审核，跳过生成",
                        call.getCallNo(), group.stockOrgNumber, billNo);
            } else {
                try {
                    KingdeeService.ReturnOrderResult retResult = kingdeeService.createReturnOrder(
                            date, group.stockOrgNumber, group.prdOrgNumber, description,
                            group.entries, resumeBillNo);
                    billNo = retResult.getBillNo();
                } catch (Exception e) {
                    // 已成功Save的分组单号随异常带出，重试时续传避免重复建单
                    throw new KingdeeService.BillStageException("PRD_ReturnMtrl",
                            joinCsv(retBillNos),
                            "库存组织[" + group.stockOrgNumber + "]退料单生成失败: " + e.getMessage());
                }
                log.info("[补退料] 申请{} 库存组织{}退料单已生成: {} ({}行分录)",
                        call.getCallNo(), group.stockOrgNumber, billNo, group.entries.size());
            }
            if (billNo != null && !billNo.isEmpty()) {
                retBillNos.add(billNo);
                assignedBills.add(billNo);
            }
        }
        // 未被任何组织分组匹配的旧单号：多为修复前生成的错误组织(如100)草稿，不续传不回写，记日志待人工清理
        for (String old : existBills) {
            if (!assignedBills.contains(old)) {
                log.warn("[补退料] 申请{} 旧退料单{}（库存组织{}）与领料来源不匹配，已放弃续传并重新建单，请人工确认/删除该草稿",
                        call.getCallNo(), old, existBillOrg.get(old));
            }
        }
        if (retBillNos.isEmpty()) {
            throw new KingdeeService.KingdeeApiException("退料单生成失败：无有效分组");
        }
        String retBillNoCsv = joinCsv(retBillNos);

        // 补料单：已审核跳过；有续传单号走续传；否则新建
        // 注意：补料单创建逻辑当前被临时注释，feedBillNo置null（下游写入erpReplenishOrderNo允许为空）
        String feedBillNo = null;
        /***boolean feedDone = isBillAudited("PRD_FeedMtrl", resumeFeedBillNo);
        if (feedDone) {
            feedBillNo = resumeFeedBillNo;
        } else {
            KingdeeService.ReturnOrderResult feedResult = kingdeeService.createFeedOrder(
                    date, headerStockOrg, headerPrdOrg, headerWorkshop, headerStock, description,
                    call.getApplicantName(), feedEntries, resumeFeedBillNo);
            feedBillNo = feedResult.getBillNo();
        }***/
        return new String[]{retBillNoCsv, feedBillNo};
    }

    /**
     * 为退料单分组匹配已有单号：
     * 1) 优先已审核(C)且库存组织一致的单据 → 跳过；
     * 2) 其次未审核且库存组织一致的草稿 → 续传（避免重复建单）；
     * 3) 库存组织不一致的旧单号（如修复前默认100）不续传；
     * 4) 仅当整单只有一个分组、且存在查询不到组织的旧单号时，兜底续传该单号。
     */
    private String matchResumeBill(ReturnOrderGroup group, List<String> existBills,
                                   Map<String, String> existBillOrg, Set<String> auditedBills,
                                   Set<String> assignedBills) {
        // 已审核 + 组织一致（组织查询失败时null也允许，已审核单据必须跳过避免重复）
        for (String billNo : existBills) {
            if (assignedBills.contains(billNo) || !auditedBills.contains(billNo)) continue;
            String org = existBillOrg.get(billNo);
            if (org == null || org.equals(group.stockOrgNumber)) return billNo;
        }
        // 未审核草稿 + 组织明确一致 → 续传
        for (String billNo : existBills) {
            if (assignedBills.contains(billNo) || auditedBills.contains(billNo)) continue;
            if (group.stockOrgNumber.equals(existBillOrg.get(billNo))) return billNo;
        }
        // 兜底：组织查询失败(null)的未审核草稿，且没有其他分组竞争时续传
        for (String billNo : existBills) {
            if (assignedBills.contains(billNo) || auditedBills.contains(billNo)) continue;
            if (existBillOrg.get(billNo) == null) return billNo;
        }
        return null;
    }

    /**
     * 按生产订单分录+物料+批次匹配生产领料单实际发料记录：
     * 优先同批次记录（启用批次/FIFO时即该批次来源仓库）；无批次信息时取最近一次领料(FDate已降序)。
     * 查不到发料记录时拦截，明确报错，不允许回退默认组织/仓库。
     */
    private KingdeeService.PickStockRow resolvePickStock(DbMaterialCall call,
                                                         List<KingdeeService.PickStockRow> pickRows,
                                                         Long moEntryId, String orderCode,
                                                         String materialCode, String batchNo) {
        List<KingdeeService.PickStockRow> candidates = pickRows.stream()
                .filter(r -> Objects.equals(r.getMoEntryId(), moEntryId)
                        && materialCode.equals(r.getMaterialCode()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            throw new RuntimeException("未查询到生产订单[" + orderCode + "]物料[" + materialCode
                    + "]的已审核生产领料单发料记录，无法确定退料仓库/库存组织，已拦截生成（不允许使用默认组织）");
        }
        KingdeeService.PickStockRow picked = null;
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            picked = candidates.stream()
                    .filter(r -> batchNo.equals(r.getLotNumber()))
                    .findFirst().orElse(null);
        }
        if (picked == null) {
            picked = candidates.get(0); // 查询已按FDate desc排序 → 最近一次领料
            log.info("[补退料] 申请{} 订单{}物料{}批次{}未匹配到同批次领料记录，回退最近一次领料单{}/{}/{}",
                    call.getCallNo(), orderCode, materialCode, batchNo,
                    picked.getBillNo(), picked.getStockOrgNumber(), picked.getStockNumber());
        } else {
            log.info("[补退料] 申请{} 订单{}物料{}批次{}匹配领料单{} 组织{} 仓库{} 仓位{}",
                    call.getCallNo(), orderCode, materialCode, batchNo,
                    picked.getBillNo(), picked.getStockOrgNumber(), picked.getStockNumber(),
                    picked.getStockLocId());
        }
        if (picked.getStockOrgNumber() == null || picked.getStockOrgNumber().isEmpty()
                || picked.getStockNumber() == null || picked.getStockNumber().isEmpty()) {
            throw new RuntimeException("生产订单[" + orderCode + "]物料[" + materialCode
                    + "]的领料单" + picked.getBillNo() + "缺少发料库存组织/仓库，已拦截生成");
        }
        if (picked.getOwnerNumber() != null && !picked.getOwnerNumber().equals(picked.getStockOrgNumber())) {
            log.warn("[补退料] 申请{} 领料单{}货主{}与发料组织{}不一致，退料单货主按发料组织填写，请核查",
                    call.getCallNo(), picked.getBillNo(), picked.getOwnerNumber(), picked.getStockOrgNumber());
        }
        return picked;
    }

    /** 逗号分隔单号 → 去空去重列表 */
    private List<String> splitCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct()
                .collect(Collectors.toList());
    }

    /** 列表 → 逗号分隔字符串 */
    private String joinCsv(List<String> nos) {
        return nos == null ? "" : nos.stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .distinct().collect(Collectors.joining(","));
    }

    /** 合并两批逗号分隔单号（去重保序） */
    private String mergeCsv(String csv1, String csv2) {
        LinkedHashSet<String> all = new LinkedHashSet<>(splitCsv(csv1));
        all.addAll(splitCsv(csv2));
        return String.join(",", all);
    }

    /**
     * 仓位内码回填：升级前匹配的历史批次行LOCATION_ID为空时，
     * 按物料+批次+仓库从金蝶即时库存查询仓位内码并回写DB（自愈旧数据，避免每次重试重复查询）。
     */
    private void backfillLocationIds(DbMaterialCall call, List<DbMaterialCallBatch> batches) {
        List<String> missingMaterials = batches.stream()
                .filter(b -> b.getLocationId() == null)
                .map(DbMaterialCallBatch::getMaterialCode).distinct().collect(Collectors.toList());
        if (missingMaterials.isEmpty()) return;

        Map<String, Long> locMap = new HashMap<>();
        for (KingdeeService.StockRow row : kingdeeService.queryStock(missingMaterials)) {
            if (row.getLocationId() != null && row.getLocationId() > 0) {
                locMap.put(stockKey(row.getMaterialCode(), row.getLotNumber(), row.getStockNumber()),
                        row.getLocationId());
            }
        }
        for (DbMaterialCallBatch b : batches) {
            if (b.getLocationId() != null) continue;
            Long loc = locMap.get(stockKey(b.getMaterialCode(), b.getBatchNo(), b.getWarehouseCode()));
            if (loc == null) continue;
            b.setLocationId(loc);
            DbMaterialCallBatch upd = new DbMaterialCallBatch();
            upd.setId(b.getId());
            upd.setLocationId(loc);
            batchMapper.updateById(upd);
            log.info("[补退料] 申请{} 批次行{}仓位内码回填: {}", call.getCallNo(),
                    stockKey(b.getMaterialCode(), b.getBatchNo(), b.getWarehouseCode()), loc);
        }
    }

    /** 即时库存行键：物料|批次|仓库 */
    private String stockKey(String materialCode, String lotNumber, String warehouseCode) {
        return materialCode + "|" + lotNumber + "|" + warehouseCode;
    }

    /** 按用料清单单号View加载分录详情，返回 订单|物料 → 分录详情 映射 */
    private Map<String, KingdeeService.PpbomEntryDetail> loadPpbomEntries(
            Map<String, KingdeeService.PpbomRow> ppbomMap) {
        Map<String, KingdeeService.PpbomEntryDetail> result = new HashMap<>();
        Set<String> viewedBillNos = new HashSet<>();
        for (KingdeeService.PpbomRow row : ppbomMap.values()) {
            if (row.getPpbomBillNo() == null || !viewedBillNos.add(row.getPpbomBillNo())) continue;
            for (KingdeeService.PpbomEntryDetail d : kingdeeService.viewPpbom(row.getPpbomBillNo())) {
                log.info("[补退料] viewPpbom({}) 分录: moBillNo={}, material={}, entryId={}, seq={}",
                        row.getPpbomBillNo(), d.getMoBillNo(), d.getMaterialNumber(),
                        d.getEntryId(), d.getEntrySeq());
                if (d.getMoBillNo() != null && d.getMaterialNumber() != null) {
                    result.put(ppbomKey(d.getMoBillNo(), d.getMaterialNumber()), d);
                }
            }
        }
        return result;
    }

    // ==================================================================
    // M7 WMS 出库申请 + 流程收尾（40→50→99 / 41→50→99）
    // ==================================================================

    private MaterialResult<MaterialCallOpResultDTO> finishWmsAndComplete(String callId) {
        DbMaterialCall call = mustGetCall(callId);
        List<DbMaterialCallBatch> batches = batchMapper.selectByCallId(callId);
        List<DbMaterialCallBatch> wmsBatches = batches.stream()
                .filter(b -> Objects.equals(b.getWmsFlag(), 1))
                .collect(Collectors.toList());

        // 无WMS批次：直接完成
        if (wmsBatches.isEmpty()) {
            updateStatus(callId, call.getStatus(), DbMaterialCall.STATUS_FINISHED, null);
            return MaterialResult.ok(opResult(call.getId(), call.getCallNo(),
                    DbMaterialCall.STATUS_FINISHED, call.getErpOrderNo(),
                    call.getErpReplenishOrderNo(), null));
        }

        // 标记涉及WMS
        callMapper.update(null, new LambdaUpdateWrapper<DbMaterialCall>()
                .eq(DbMaterialCall::getId, callId)
                .set(DbMaterialCall::getWmsEnabled, 1));

        // 调WMS出库申请
        try {
            String wmsOrderNo = callWms(call, wmsBatches);
            updateStatus(callId, call.getStatus(), DbMaterialCall.STATUS_WMS_CREATED, uw ->
                    uw.set(DbMaterialCall::getWmsOrderNo, wmsOrderNo).set(DbMaterialCall::getErrorMsg, null));
            updateStatus(callId, DbMaterialCall.STATUS_WMS_CREATED, DbMaterialCall.STATUS_FINISHED, null);
            DbMaterialCall latest = mustGetCall(callId);
            messageService.push("audit-pass", "仓管员",
                    "补退料申请 " + call.getCallNo() + " 已完成\n\n金蝶退料单号：" + call.getErpOrderNo()
                            + "\n金蝶补料单号：" + call.getErpReplenishOrderNo()
                            + "\nWMS出库申请单号：" + wmsOrderNo);
            return MaterialResult.ok(opResult(latest.getId(), latest.getCallNo(),
                    DbMaterialCall.STATUS_FINISHED, latest.getErpOrderNo(),
                    latest.getErpReplenishOrderNo(), latest.getWmsOrderNo()));
        } catch (Exception e) {
            log.error("[补退料] 申请{}WMS出库申请失败: {}", call.getCallNo(), e.getMessage(), e);
            String errMsg = truncateDb(e.getMessage(), 500);
            callMapper.update(null, new LambdaUpdateWrapper<DbMaterialCall>()
                    .eq(DbMaterialCall::getId, callId)
                    .set(DbMaterialCall::getStatus, DbMaterialCall.STATUS_WMS_ERROR)
                    .set(DbMaterialCall::getErrorMsg, errMsg));
            messageService.push("wms-error", "admin",
                    "补退料申请 " + call.getCallNo() + " WMS出库申请异常：" + errMsg);
            return MaterialResult.error("WMS出库申请失败：" + errMsg);
        }
    }

    /** 按仓库分组调用WMS出库申请，多个仓库时单号以逗号连接 */
    private String callWms(DbMaterialCall call, List<DbMaterialCallBatch> wmsBatches) {
        Map<String, List<DbMaterialCallBatch>> byWarehouse = wmsBatches.stream()
                .collect(Collectors.groupingBy(b -> b.getWarehouseCode() == null ? "" : b.getWarehouseCode(),
                        LinkedHashMap::new, Collectors.toList()));
        List<String> orderNos = new ArrayList<>();
        for (Map.Entry<String, List<DbMaterialCallBatch>> entry : byWarehouse.entrySet()) {
            List<WmsService.OutboundItem> outboundItems = new ArrayList<>();
            String warehouseName = null;
            for (DbMaterialCallBatch b : entry.getValue()) {
                outboundItems.add(new WmsService.OutboundItem(b.getMaterialCode(), b.getBatchNo(),
                        b.getLocationCode(), b.getQty()));
                if (warehouseName == null) warehouseName = b.getWarehouse();
            }
            orderNos.add(wmsService.applyOutbound(call.getCallNo(), call.getErpOrderNo(),
                    warehouseName, outboundItems));
        }
        return String.join(",", orderNos);
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    private DbMaterialCall mustGetCall(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("申请单ID不能为空");
        }
        DbMaterialCall call = callMapper.selectById(id.trim());
        if (call == null) {
            throw new IllegalArgumentException("申请单不存在：" + id);
        }
        return call;
    }

    /** 带乐观状态检查的状态更新；extra 可追加set */
    private int updateStatus(String id, Integer fromStatus, Integer toStatus,
                             java.util.function.Consumer<LambdaUpdateWrapper<DbMaterialCall>> extra) {
        LambdaUpdateWrapper<DbMaterialCall> uw = new LambdaUpdateWrapper<>();
        uw.eq(DbMaterialCall::getId, id)
                .eq(DbMaterialCall::getStatus, fromStatus)
                .set(DbMaterialCall::getStatus, toStatus)
                .set(DbMaterialCall::getUpdateTime, LocalDateTime.now());
        if (extra != null) extra.accept(uw);
        return callMapper.update(null, uw);
    }

    private MaterialCallOpResultDTO opResult(String id, String callNo, Integer status,
                                             String erpOrderNo, String erpReplenishOrderNo, String wmsOrderNo) {
        MaterialCallOpResultDTO dto = new MaterialCallOpResultDTO();
        dto.setId(id);
        dto.setCallNo(callNo);
        dto.setStatus(status);
        dto.setErpOrderNo(erpOrderNo);
        dto.setErpReplenishOrderNo(erpReplenishOrderNo);
        dto.setWmsOrderNo(wmsOrderNo);
        return dto;
    }

    private MaterialApplicationDTO toApplicationDTO(DbMaterialCall call, List<DbMaterialCallItem> items,
                                                    List<DbMaterialCallBatch> batches) {
        MaterialApplicationDTO dto = new MaterialApplicationDTO();
        dto.setId(call.getId());
        dto.setCallNo(call.getCallNo());
        dto.setStatus(call.getStatus());
        dto.setCreateTime(fmt(call.getCreateTime()));
        dto.setApplicantName(call.getApplicantName());
        dto.setApplicantDept(call.getApplicantDept());
        dto.setQcStaffName(call.getQcStaffName());
        dto.setReturnTypeText(call.getReturnTypeName());
        dto.setReasonId(call.getReasonId());
        dto.setReasonText(call.getReasonText());
        dto.setErpOrderNo(call.getErpOrderNo());
        dto.setErpReplenishOrderNo(call.getErpReplenishOrderNo());
        dto.setWmsOrderNo(call.getWmsOrderNo());
        dto.setRejectReason(call.getRejectReason());
        dto.setErrorMsg(call.getErrorMsg());
        dto.setAuditTime(fmt(call.getAuditTime()));
        dto.setAuditBy(call.getAuditBy());
        dto.setWmsEnabled(Objects.equals(call.getWmsEnabled(), 1));
        dto.setItemCount(items == null ? 0 : items.size());
        dto.setOrderCodes(items == null ? "" : items.stream()
                .map(DbMaterialCallItem::getOrderCode).distinct().collect(Collectors.joining(",")));
        if (items != null) {
            dto.setItems(items.stream().map(it -> {
                MaterialApplicationDTO.ItemDTO i = new MaterialApplicationDTO.ItemDTO();
                i.setOrderCode(it.getOrderCode());
                i.setMaterialCode(it.getMaterialCode());
                i.setMaterialName(it.getMaterialName());
                i.setSpec(it.getSpec());
                i.setUnit(it.getUnit());
                i.setQty(scale2(it.getQty()));
                return i;
            }).collect(Collectors.toList()));
        }
        if (batches != null) {
            dto.setBatches(batches.stream().map(b -> {
                MaterialApplicationDTO.BatchDTO bd = new MaterialApplicationDTO.BatchDTO();
                bd.setMaterialCode(b.getMaterialCode());
                bd.setBatchNo(b.getBatchNo());
                bd.setLocationCode(b.getLocationCode());
                bd.setWarehouse(b.getWarehouse());
                bd.setQty(scale2(b.getQty()));
                bd.setWmsFlag(Objects.equals(b.getWmsFlag(), 1));
                return bd;
            }).collect(Collectors.toList()));
        }
        return dto;
    }

    /** 即时库存按物料汇总可用量 */
    private Map<String, BigDecimal> queryStockSum(List<String> materialCodes) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (materialCodes == null || materialCodes.isEmpty()) return map;
        for (KingdeeService.StockRow row : kingdeeService.queryStock(materialCodes)) {
            map.merge(row.getMaterialCode(), nz(row.getBaseQty()), BigDecimal::add);
        }
        return map;
    }

    private List<Integer> parseStatuses(String status) {
        if (status == null || status.trim().isEmpty()) return null;
        List<Integer> result = new ArrayList<>();
        for (String token : status.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            try {
                result.add(Integer.parseInt(t));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("状态筛选不合法：" + status);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private LocalDateTime parseStartTime(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String t = s.trim();
        if (t.length() == 10) return LocalDate.parse(t, FMT_DATE).atStartOfDay();
        return LocalDateTime.parse(t, FMT);
    }

    private LocalDateTime parseEndTime(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String t = s.trim();
        if (t.length() == 10) return LocalDate.parse(t, FMT_DATE).atTime(LocalTime.MAX);
        return LocalDateTime.parse(t, FMT);
    }

    private String ppbomKey(String orderCode, String materialCode) {
        return orderCode + "|" + materialCode;
    }

    /** 金蝶FStatus → 前端orderStatus：3下达→1，4开工→2，5/6完工结案→3，其他→0 */
    private Integer mapOrderStatus(String fStatus) {
        switch (fStatus == null ? "" : fStatus) {
            case "3": return 1;
            case "4": return 2;
            case "5":
            case "6": return 3;
            default: return 0;
        }
    }

    /** 金蝶FStatus文本 */
    private String moStatusText(String fStatus) {
        switch (fStatus == null ? "" : fStatus) {
            case "1": return "计划";
            case "2": return "计划确认";
            case "3": return "已下达";
            case "4": return "开工";
            case "5": return "已完工";
            case "6": return "已结案";
            case "7": return "已结算";
            default: return "未知(" + fStatus + ")";
        }
    }

    /** 申请单状态文本 */
    private String statusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case DbMaterialCall.STATUS_PENDING_AUDIT: return "待质检审核";
            case DbMaterialCall.STATUS_REJECTED: return "已驳回";
            case DbMaterialCall.STATUS_MATCHING: return "批次匹配中";
            case DbMaterialCall.STATUS_ERP_CREATING: return "退料单生成中";
            case DbMaterialCall.STATUS_ERP_ERROR: return "退料单生成异常";
            case DbMaterialCall.STATUS_ERP_CREATED: return "退料单已生成";
            case DbMaterialCall.STATUS_WMS_ERROR: return "WMS申请异常";
            case DbMaterialCall.STATUS_WMS_CREATED: return "WMS申请已生成";
            case DbMaterialCall.STATUS_FINISHED: return "已完成";
            default: return "未知(" + status + ")";
        }
    }

    private List<String> distinctTrimmed(List<String> list) {
        if (list == null) return Collections.emptyList();
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal scale2(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }

    /** 金蝶日期 "2026-09-25T00:00:00" → "2026-09-25" */
    private String datePart(String kingdeeDate) {
        if (kingdeeDate == null || kingdeeDate.isEmpty()) return "";
        int idx = kingdeeDate.indexOf('T');
        return idx > 0 ? kingdeeDate.substring(0, idx) : kingdeeDate;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * DB列VARCHAR(n)按字节存储（中文2字节），按字符数截断会导致"将截断字符串"更新失败。
     * 按最坏情况（每字符2字节）截断到 maxBytes/2 字符。
     */
    private String truncateDb(String s, int maxBytes) {
        if (s == null) return "";
        int maxChars = maxBytes / 2;
        return s.length() > maxChars ? s.substring(0, maxChars) : s;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
