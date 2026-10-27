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
    @Resource
    private WagePayStatService payStatService;

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

    /**
     * 从金蝶同步生产任务单：先查 PRD_PPBOM 子项含"丝印/移印"的工单 → 再查 PRD_MO 工单头 → upsert。
     * <p>过滤逻辑：只有 BOM 子项物料名称包含"丝印"或"移印"的工单才进工资核算。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncFromMes() {
        // Step 1: 从 PRD_PPBOM 查近 12 个月含"丝印/移印"子项的工单
        List<String> keywords = List.of("丝印", "移印");
        List<WagePpbomRowVO> ppbomRows = kingdeeWageService.queryPpbomByKeyword(keywords, null, 5000);
        if (ppbomRows.isEmpty()) {
            log.warn("[工资核算] 金蝶 PRD_PPBOM 同步：近 12 个月无丝印/移印子项");
            return 0;
        }
        // 提取 distinct 工单号
        Set<String> taskNos = new LinkedHashSet<>();
        for (WagePpbomRowVO r : ppbomRows) {
            if (r.getMoBillNo() != null && !r.getMoBillNo().isEmpty()) {
                taskNos.add(r.getMoBillNo());
            }
        }
        log.info("[工资核算] PRD_PPBOM 命中 {} 张含丝印/移印子项的工单", taskNos.size());

        // Step 2: 拿工单号去 PRD_MO 查工单头
        List<WageMoRowVO> kdOrders = kingdeeWageService.queryMoByBillNos(new ArrayList<>(taskNos));
        if (kdOrders.isEmpty()) {
            log.warn("[工资核算] PRD_MO 查询返回空");
            return 0;
        }

        // Step 3: upsert 到 wage_order
        int added = 0;
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (WageMoRowVO kd : kdOrders) {
            String taskNo = kd.getMoBillNo();
            if (taskNo == null || taskNo.isEmpty()) continue;

            WageOrder existing = orderMapper.selectByTaskNo(taskNo);
            if (existing != null) {
                existing.setProductName(kd.getProductName());
                existing.setMaterialCode(kd.getProductCode());
                existing.setProductQty(kd.getQty());
                existing.setStockInQty(kd.getYieldQty());
                existing.setWorkshop(kd.getWorkshop());
                // 开工/完工时间是本地业务字段（开工提交、完工录入填的），不从金蝶覆盖
                existing.setSyncTime(now);
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
                // startTime / finishTime 为 null，等本地开工/完工时填充
                o.setBizStatus("计划");
                o.setConfirmStatus("未确认");
                o.setSettleStatus("待结算");
                o.setSyncTime(now);
                orderMapper.insert(o);
                added++;
            }
        }
        log.info("[工资核算] 金蝶工单头同步完成：新增 {} 张，更新 {} 张", added, updated);

        // Step 4: 把命中的丝印/移印子项也 upsert 到 wage_material
        int matAdded = 0;
        int matUpdated = 0;
        for (WagePpbomRowVO pp : ppbomRows) {
            String taskNo = pp.getMoBillNo();
            String matCode = pp.getMaterialCode();
            if (taskNo == null || matCode == null) continue;

            if (materialMapper.countByTaskAndMaterial(taskNo, matCode) > 0) {
                // 已存在 → 更新数量/名称（金蝶可能有变更）
                WageMaterial exist = materialMapper.selectOne(
                        new LambdaQueryWrapper<WageMaterial>()
                                .eq(WageMaterial::getTaskNo, taskNo)
                                .eq(WageMaterial::getMaterialCode, matCode)
                                .eq(WageMaterial::getDeleted, 0));
                if (exist != null) {
                    exist.setMaterialName(pp.getMaterialName());
                    exist.setUnit(pp.getUnitName());
                    exist.setQty(pp.getNeedQty());
                    exist.setFeedNo(pp.getPpbomBillNo());
                    exist.setUpdateTime(now);
                    materialMapper.updateById(exist);
                    matUpdated++;
                }
            } else {
                WageMaterial m = new WageMaterial();
                m.setTaskNo(taskNo);
                m.setFeedNo(pp.getPpbomBillNo());
                m.setMaterialCode(matCode);
                m.setMaterialName(pp.getMaterialName());
                m.setUnit(pp.getUnitName());
                m.setQty(pp.getNeedQty());
                m.setBizStatus("计划");
                materialMapper.insert(m);
                matAdded++;
            }
        }
        log.info("[工资核算] 金蝶用料子项同步完成：新增 {} 条，更新 {} 条（丝印/移印子项）", matAdded, matUpdated);
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

        // 自动触发计件明细重算（全量删旧重建，确保新工单的用料能进计件复核）
        try {
            String operator = req.getConfirmerName() == null ? req.getConfirmerNo() : req.getConfirmerName();
            int n = payStatService.recalc(operator == null ? "开工自动" : operator);
            log.info("[工资核算] 开工自动重算计件明细：生成 {} 条（工单号 {}）", n, order.getTaskNo());
        } catch (Exception e) {
            log.warn("[工资核算] 开工自动重算计件明细失败（不阻断开工）：{}", e.getMessage());
        }
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

        // 完工时也触发计件明细重算（状态变更后可能影响分摊基数）
        try {
            int n = payStatService.recalc(req.getFinishUser() == null ? "完工自动" : req.getFinishUser());
            log.info("[工资核算] 完工自动重算计件明细：生成 {} 条（工单号 {}）", n, order.getTaskNo());
        } catch (Exception e) {
            log.warn("[工资核算] 完工自动重算计件明细失败（不阻断完工）：{}", e.getMessage());
        }
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
     * <p>幂等 + 覆盖：已存在的 task_no 会 UPDATE 金蝶字段（bizStatus/confirmStatus/settleStatus 等本地业务字段一律保留，不同步自金蝶），
     * 缺失的 INSERT 时 bizStatus 默认「计划」。</p>
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
        LocalDateTime now = LocalDateTime.now();
        for (WageMoRowVO kd : kdOrders) {
            String taskNo = kd.getMoBillNo();
            if (taskNo == null || taskNo.isEmpty()) continue;
            WageOrder existing = orderMapper.selectByTaskNo(taskNo);
            if (existing != null) {
                // UPDATE：只更新从金蝶能拿到的字段
                // 注意：startTime/finishTime 是本地业务字段（开工提交/完工录入时用 now() 填入），
                //       金蝶的 FPlanStartDate/FPlanFinishDate 是计划时间，不覆盖
                //       bizStatus/confirmStatus/settleStatus/gid 等也一律保留本地值
                existing.setProductName(kd.getProductName());
                existing.setMaterialCode(kd.getProductCode());
                existing.setProductQty(kd.getQty());
                existing.setStockInQty(kd.getYieldQty());
                existing.setWorkshop(kd.getWorkshop());
                existing.setSyncTime(now);
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
                // startTime / finishTime 为 null，等本地 start() / done() 时用 now() 填入真实时间
                o.setBizStatus("计划");
                o.setConfirmStatus("未确认");
                o.setSettleStatus("待结算");
                o.setSyncTime(now);
                orderMapper.insert(o);
                added++;
            }
        }
        log.info("[工资核算] syncFromMaterials：wage_material {} 个 task_no，PRD_MO 返回 {} 个，新增 {} / 更新 {}",
                materialTaskNos.size(), kdOrders.size(), added, updated);
        return added;
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
