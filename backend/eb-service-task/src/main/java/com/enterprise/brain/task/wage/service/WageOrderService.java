package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.entity.MesMaterialDetail;
import com.enterprise.brain.task.entity.MesProductOrder;
import com.enterprise.brain.task.mapper.MesProductOrderMapper;
import com.enterprise.brain.task.service.KingdeeService;
import com.enterprise.brain.task.wage.dto.PageVO;
import com.enterprise.brain.task.wage.dto.WageMoRowVO;
import com.enterprise.brain.task.wage.dto.WageOrderDoneRequest;
import com.enterprise.brain.task.wage.dto.WageOrderStartRequest;
import com.enterprise.brain.task.wage.dto.WageOrderVO;
import com.enterprise.brain.task.wage.dto.WagePpbomRowVO;
import com.enterprise.brain.task.wage.entity.WageConfirm;
import com.enterprise.brain.task.wage.entity.WageGroup;
import com.enterprise.brain.task.wage.entity.WageMaterial;
import com.enterprise.brain.task.wage.entity.WageOrder;
import com.enterprise.brain.task.wage.mapper.WageConfirmMapper;
import com.enterprise.brain.task.wage.mapper.WageGroupMapper;
import com.enterprise.brain.task.wage.mapper.WageMaterialMapper;
import com.enterprise.brain.task.wage.mapper.WageOrderMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 生产任务单 + 用料清单：MES 同步、开工提交、确认完工
 */
@Slf4j
@Service
public class WageOrderService {

    @Resource
    private WageOrderMapper orderMapper;
    @Resource
    private WageMaterialMapper materialMapper;
    @Resource
    private WageConfirmMapper confirmMapper;
    @Resource
    private WageGroupMapper groupMapper;
    @Resource
    private MesProductOrderMapper mesOrderMapper;
    @Resource
    private KingdeeService kingdeeService;
    @Resource
    private KingdeeWageService kingdeeWageService;
    @Resource
    private WagePriceService priceService;

    // ------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------

    public PageVO<WageOrderVO> page(String workshop, Long groupId, String settleStatus,
                                    String bizStatus, String keyword,
                                    int pageNum, int pageSize) {
        List<WageOrderVO> all = orderMapper.selectOrderList(
                trimToNull(workshop), groupId, trimToNull(settleStatus),
                trimToNull(bizStatus), trimToNull(keyword));
        return PageVO.of(all, pageNum, pageSize);
    }

    public List<WageMaterial> materials(String taskNo, String bizStatus, String keyword) {
        return materialMapper.selectMaterials(trimToNull(taskNo), trimToNull(bizStatus), trimToNull(keyword));
    }

    /** 任务单号下拉候选（orders ∪ materials 去重） */
    public List<String> taskNoOptions() {
        Set<String> set = new LinkedHashSet<>();
        orderMapper.selectList(new LambdaQueryWrapper<WageOrder>().select(WageOrder::getTaskNo))
                .forEach(o -> { if (o.getTaskNo() != null) set.add(o.getTaskNo()); });
        materialMapper.selectList(new LambdaQueryWrapper<WageMaterial>().select(WageMaterial::getTaskNo))
                .forEach(m -> { if (m.getTaskNo() != null) set.add(m.getTaskNo()); });
        List<String> list = new ArrayList<>(set);
        list.sort(String::compareTo);
        return list;
    }

    /** 车间下拉候选（任务单实际出现值） */
    public List<String> workshopOptions() {
        Set<String> set = new LinkedHashSet<>();
        orderMapper.selectList(new LambdaQueryWrapper<WageOrder>().select(WageOrder::getWorkshop))
                .forEach(o -> { if (o.getWorkshop() != null && !o.getWorkshop().isEmpty()) set.add(o.getWorkshop()); });
        return new ArrayList<>(set);
    }

    // ------------------------------------------------------------
    // MES 同步
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public int syncFromMes() {
        List<MesProductOrder> mesList = mesOrderMapper.selectWageSyncList();
        int added = 0;
        LocalDateTime now = LocalDateTime.now();
        for (MesProductOrder mes : mesList) {
            String taskNo = mes.getPcode();
            if (taskNo == null || taskNo.isEmpty() || orderMapper.selectByTaskNo(taskNo) != null) {
                continue;
            }
            WageOrder o = new WageOrder();
            o.setTaskNo(taskNo);
            o.setProductName(mes.getMaterialname());
            o.setMaterialCode(mes.getMaterialid());
            o.setSubMaterialName(mes.getMaterialname());
            o.setProductQty(mes.getTotal() == null ? null : new BigDecimal(mes.getTotal()));
            o.setWorkshop(mes.getDept());
            o.setConfirmStatus("未确认");
            o.setBizStatus("计划");
            o.setSettleStatus("待结算");
            o.setSyncTime(now);
            orderMapper.insert(o);
            added++;
        }
        log.info("[工资核算] MES 任务单同步完成，新增 {} 张", added);
        return added;
    }

    // ------------------------------------------------------------
    // 开工提交
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public void start(Long id, WageOrderStartRequest req) {
        WageOrder order = mustGet(id);
        if (!"计划".equals(order.getBizStatus())) {
            throw new BusinessException("仅计划状态的任务单可开工提交");
        }
        WageGroup group = groupMapper.selectById(req.getGroupId());
        if (group == null) {
            throw new BusinessException("请选择有效的加工组别");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setGroupId(group.getId());
        order.setBizStatus("开工");
        order.setStartTime(now);
        order.setConfirmStatus("已确认");
        order.setSettleStatus("已提交");
        if (order.getProcessName() == null || order.getProcessName().isEmpty()) {
            order.setProcessName(group.getDefaultProcess());
        }
        orderMapper.updateById(order);

        // 开工确认留痕
        WageConfirm confirm = new WageConfirm();
        confirm.setTaskNo(order.getTaskNo());
        confirm.setMaterialCode(order.getMaterialCode());
        confirm.setSubMaterialName(order.getSubMaterialName());
        confirm.setGroupId(group.getId());
        confirm.setProcessName(order.getProcessName());
        confirm.setConfirmQty(order.getProductQty());
        confirm.setErpQty(order.getProductQty());
        confirm.setConfirmerNo(req.getConfirmerNo());
        confirm.setConfirmTime(now);
        confirm.setConfirmStatus("已确认");
        confirmMapper.insert(confirm);

        // 金蝶《生产用料清单》下发（失败不阻断开工，后续可在该单再次触发/人工补录）
        pushMaterialsFromKingdee(order);
    }

    private void pushMaterialsFromKingdee(WageOrder order) {
        String materialId = order.getMaterialCode();
        if (materialId == null || materialId.isEmpty()) {
            log.warn("[工资核算] 任务单 {} 无产品物料编码，跳过金蝶用料清单下发", order.getTaskNo());
            return;
        }
        try {
            KingdeeService.LoginResult session = kingdeeService.getSession();
            List<MesMaterialDetail> bom = kingdeeService.queryBom(order.getTaskNo(), materialId, session);
            int added = 0;
            for (MesMaterialDetail d : bom) {
                if (d.getMaterialCode() == null || d.getMaterialCode().isEmpty()) {
                    continue;
                }
                // 工资核算只关心丝印/移印工序相关物料，其余跳过
                String name = d.getMaterialName();
                if (name == null || (!name.contains("丝印") && !name.contains("移印"))) {
                    continue;
                }
                if (materialMapper.countByTaskAndMaterial(order.getTaskNo(), d.getMaterialCode()) > 0) {
                    continue;
                }
                WageMaterial m = new WageMaterial();
                m.setTaskNo(order.getTaskNo());
                m.setFeedNo(order.getFeedNo());
                m.setBizStatus("开工");
                m.setMaterialCode(d.getMaterialCode());
                m.setMaterialName(d.getMaterialName());
                m.setUnit(d.getUnit());
                m.setQty(d.getRequiredQty() == null ? BigDecimal.ZERO : new BigDecimal(d.getRequiredQty()));
                materialMapper.insert(m);
                added++;
            }
            log.info("[工资核算] 任务单 {} 金蝶用料清单下发 {} 行", order.getTaskNo(), added);
        } catch (Exception e) {
            log.warn("[工资核算] 任务单 {} 金蝶用料清单下发失败（不阻断开工）：{}",
                    order.getTaskNo(), e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // 确认完工
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public void done(Long id, WageOrderDoneRequest req) {
        WageOrder order = mustGet(id);
        if (!"开工".equals(order.getBizStatus())) {
            throw new BusinessException("仅开工状态的任务单可确认完工");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setBizStatus("完工");
        order.setFinishUser(req.getFinishUser());
        order.setFinishTime(now);
        order.setStockInQty(req.getStockInQty());
        order.setFinishQty(req.getStockInQty());
        orderMapper.updateById(order);

        materialMapper.updateBizStatusByTaskNo(order.getTaskNo(), "完工");
    }

    // ------------------------------------------------------------
    // 改组别 / 删除
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public void changeGroup(Long id, Long groupId) {
        WageOrder order = mustGet(id);
        if ("完工".equals(order.getBizStatus())) {
            throw new BusinessException("已完工任务单不允许修改组别");
        }
        if (groupMapper.selectById(groupId) == null) {
            throw new BusinessException("请选择有效的加工组别");
        }
        order.setGroupId(groupId);
        orderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        WageOrder order = mustGet(id);
        orderMapper.deleteById(order.getId());
    }

    private WageOrder mustGet(Long id) {
        WageOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("任务单不存在或已删除");
        }
        return order;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 将金蝶 PRD_PPBOM 查询结果保存到本地 wage_material
     * <p>按 (task_no, material_code) 幂等写入：已存在的跳过，不存在的新建。</p>
     * @param rows   金蝶查询返回的用料清单行
     * @return 实际新增条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int savePpbomRows(List<WagePpbomRowVO> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        int added = 0;
        int skipped = 0;
        for (WagePpbomRowVO row : rows) {
            String taskNo = row.getMoBillNo();
            String materialCode = row.getMaterialCode();
            if (taskNo == null || taskNo.isEmpty() || materialCode == null || materialCode.isEmpty()) {
                skipped++;
                continue;
            }
            if (materialMapper.countByTaskAndMaterial(taskNo, materialCode) > 0) {
                skipped++;
                continue;
            }
            // bizStatus 取自本地 wage_order，未命中则默认"计划"
            String bizStatus = "计划";
            WageOrder exist = orderMapper.selectByTaskNo(taskNo);
            if (exist != null && exist.getBizStatus() != null && !exist.getBizStatus().isEmpty()) {
                bizStatus = exist.getBizStatus();
            }

            WageMaterial m = new WageMaterial();
            m.setTaskNo(taskNo);
            m.setFeedNo(row.getPpbomBillNo());
            m.setBizStatus(bizStatus);
            m.setMaterialCode(materialCode);
            m.setMaterialName(row.getMaterialName());
            m.setUnit(row.getUnitName());
            m.setQty(row.getNeedQty() == null ? BigDecimal.ZERO : row.getNeedQty());
            materialMapper.insert(m);
            added++;
        }
        log.info("[工资核算] 金蝶查询结果保存到本地 wage_material：新增 {}，跳过 {}（重复/空值）", added, skipped);
        return added;
    }

    /**
     * 根据本地 wage_material 里的 task_no → 调金蝶 PRD_MO 查生产订单 → 同步到 wage_order
     * <p>场景：用户先在"金蝶标准接口查询"Tab 保存用料清单到本地 wage_material，
     * 进入"生产任务单"页签时自动补齐对应的 wage_order 头信息。</p>
     * <p>幂等 + 覆盖：已存在的 task_no 会 UPDATE 金蝶字段，缺失的 INSERT。</p>
     *
     * @return 新增条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncFromMaterials() {
        // 1) 取所有 wage_material 的 distinct task_no
        List<String> materialTaskNos = materialMapper.selectList(
                new LambdaQueryWrapper<WageMaterial>()
                        .select(WageMaterial::getTaskNo)
                        .isNotNull(WageMaterial::getTaskNo))
                .stream()
                .map(WageMaterial::getTaskNo)
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .toList();

        if (materialTaskNos.isEmpty()) {
            log.info("[工资核算] syncFromMaterials：wage_material 无任务单号，跳过");
            return 0;
        }

        // 2) 调金蝶 PRD_MO.FBillNo IN (...) 查生产订单（一次性查全部）
        List<WageMoRowVO> kdOrders = kingdeeWageService.queryMoByBillNos(materialTaskNos);
        if (kdOrders.isEmpty()) {
            log.warn("[工资核算] syncFromMaterials：PRD_MO 查询返回空");
            return 0;
        }

        // 3) 逐行 upsert（已存在 UPDATE，不存在 INSERT）
        int added = 0;
        int updated = 0;
        for (WageMoRowVO kd : kdOrders) {
            String taskNo = kd.getMoBillNo();
            if (taskNo == null || taskNo.isEmpty()) continue;
            WageOrder existing = orderMapper.selectByTaskNo(taskNo);
            String bizStatus = mapMoStatusToBiz(kd.getStatus());
            if (existing != null) {
                // UPDATE：只更新从金蝶能拿到的字段（保留 confirmStatus/settleStatus/gid 等业务字段）
                existing.setProductName(kd.getProductName());
                existing.setMaterialCode(kd.getProductCode());
                existing.setProductQty(kd.getQty());
                existing.setStockInQty(kd.getYieldQty());
                existing.setWorkshop(kd.getWorkshop());
                existing.setStartTime(parseDateTime(kd.getPlanStartTime()));
                existing.setFinishTime(parseDateTime(kd.getPlanFinishTime()));
                existing.setBizStatus(bizStatus);
                existing.setSyncTime(LocalDateTime.now());
                orderMapper.updateById(existing);
                updated++;
            } else {
                WageOrder o = new WageOrder();
                o.setTaskNo(taskNo);
                o.setProductName(kd.getProductName());
                o.setMaterialCode(kd.getProductCode());
                o.setProductQty(kd.getQty());
                o.setStockInQty(kd.getYieldQty());
                o.setWorkshop(kd.getWorkshop());
                o.setStartTime(parseDateTime(kd.getPlanStartTime()));
                o.setFinishTime(parseDateTime(kd.getPlanFinishTime()));
                o.setBizStatus(bizStatus);
                o.setConfirmStatus("未确认");
                o.setSettleStatus("待结算");
                o.setSyncTime(LocalDateTime.now());
                orderMapper.insert(o);
                added++;
            }
        }
        log.info("[工资核算] syncFromMaterials：wage_material {} 个 task_no，PRD_MO 返回 {} 个，新增 {} / 更新 {}",
                materialTaskNos.size(), kdOrders.size(), added, updated);
        return added;
    }

    /**
     * 金蝶 PRD_MO.FStatus → wage_order.bizStatus
     * 1计划 2计划确认 → 计划；3下达 4开工 → 开工；5完工 6结案 7结算 → 完工；其余 → 计划
     */
    private String mapMoStatusToBiz(String fStatus) {
        if (fStatus == null) return "计划";
        switch (fStatus.trim()) {
            case "3":
            case "4": return "开工";
            case "5":
            case "6":
            case "7": return "完工";
            default:  return "计划";
        }
    }

    /** 金蝶返回的日期字符串 → LocalDateTime
     *  兼容 3 种：ISO_LOCAL_DATE_TIME(2026-10-27T00:00:00) / yyyy-MM-dd HH:mm:ss / yyyy-MM-dd
     */
    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String t = s.trim();
            // 1. 优先尝试 ISO 8601（金蝶 PRD_MO.FPlanStartDate 返回这个）
            if (t.contains("T")) {
                return LocalDateTime.parse(t);
            }
            // 2. 空格分隔 yyyy-MM-dd HH:mm:ss
            if (t.length() >= 19) {
                return LocalDateTime.parse(t.substring(0, 19),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            // 3. 纯日期 yyyy-MM-dd
            if (t.length() == 10) {
                return java.time.LocalDate.parse(t, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        .atStartOfDay();
            }
            return null;
        } catch (Exception e) {
            log.warn("[工资核算] 金蝶日期解析失败: {}", s);
            return null;
        }
    }
}
