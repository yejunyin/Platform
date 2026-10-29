package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
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
 * <p>核心变更：所有业务状态（组别/开工/完工/结算）已下沉到 wage_material，
 * 一个 task_no 下多条物料各自独立维护自己的生命周期</p>
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
    private KingdeeService kingdeeService;
    @Resource
    private KingdeeWageService kingdeeWageService;
    @Resource
    private WagePayStatService payStatService;

    // ------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------

    public PageVO<WageOrderVO> page(String workshop, String keyword,
                                    int pageNum, int pageSize) {
        List<WageOrderVO> all = orderMapper.selectOrderList(
                trimToNull(workshop), trimToNull(keyword));
        return PageVO.of(all, pageNum, pageSize);
    }

    /**
     * 用料清单查询（带加工组名称，子表级别过滤）
     */
    public List<WageMaterial> materials(String taskNo, String bizStatus, String keyword) {
        return materialMapper.selectMaterialsWithGroup(
                trimToNull(taskNo), trimToNull(bizStatus), trimToNull(keyword));
    }

    /** 任务单号下拉候选 */
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

    /** 车间下拉候选 */
    public List<String> workshopOptions() {
        Set<String> set = new LinkedHashSet<>();
        orderMapper.selectList(new LambdaQueryWrapper<WageOrder>().select(WageOrder::getWorkshop))
                .forEach(o -> { if (o.getWorkshop() != null && !o.getWorkshop().isEmpty()) set.add(o.getWorkshop()); });
        return new ArrayList<>(set);
    }

    // ------------------------------------------------------------
    // MES 同步（只写单据头到 wage_order，物料行在 Step4 写入 wage_material）
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public int syncFromMes() {
        List<String> keywords = List.of("丝印", "移印");
        List<WagePpbomRowVO> ppbomRows = kingdeeWageService.queryPpbomByKeyword(keywords, null, 5000);
        if (ppbomRows.isEmpty()) {
            log.warn("[工资核算] 金蝶 PRD_PPBOM 同步：近 12 个月无丝印/移印子项");
            return 0;
        }
        Set<String> taskNos = new LinkedHashSet<>();
        for (WagePpbomRowVO r : ppbomRows) {
            if (r.getMoBillNo() != null && !r.getMoBillNo().isEmpty()) {
                taskNos.add(r.getMoBillNo());
            }
        }
        List<WageMoRowVO> kdOrders = kingdeeWageService.queryMoByBillNos(new ArrayList<>(taskNos));
        if (kdOrders.isEmpty()) {
            log.warn("[工资核算] PRD_MO 查询返回空");
            return 0;
        }

        int added = 0, updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (WageMoRowVO kd : kdOrders) {
            String taskNo = kd.getMoBillNo();
            if (taskNo == null || taskNo.isEmpty()) continue;

            WageOrder existing = orderMapper.selectByTaskNo(taskNo);
            if (existing != null) {
                existing.setProductName(kd.getProductName());
                existing.setMaterialCode(kd.getProductCode());
                existing.setProductQty(kd.getQty());
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
                o.setWorkshop(kd.getWorkshop());
                o.setSyncTime(now);
                orderMapper.insert(o);
                added++;
            }
        }
        log.info("[工资核算] 金蝶工单头同步完成：新增 {} 张，更新 {} 张", added, updated);

        // Step 4: 丝印/移印子项 upsert 到 wage_material
        int matAdded = 0, matUpdated = 0;
        for (WagePpbomRowVO pp : ppbomRows) {
            String taskNo = pp.getMoBillNo();
            String matCode = pp.getMaterialCode();
            if (taskNo == null || matCode == null) continue;

            if (materialMapper.countByTaskAndMaterial(taskNo, matCode) > 0) {
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
                    exist.setProcessName(parseProcessName(pp.getMaterialName()));
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
                m.setProcessName(parseProcessName(pp.getMaterialName()));
                m.setBizStatus("计划");
                m.setSettleStatus("待结算");
                materialMapper.insert(m);
                matAdded++;
            }
        }
        log.info("[工资核算] 金蝶用料子项同步完成：新增 {} 条，更新 {} 条", matAdded, matUpdated);
        return added;
    }

    // ------------------------------------------------------------
    // 明细级操作（按 wage_material.id）
    // ------------------------------------------------------------

    /** 开工：按明细 ID */
    @Transactional(rollbackFor = Exception.class)
    public void startMaterial(Long materialId, WageOrderStartRequest req) {
        WageMaterial m = mustGetMaterial(materialId);
        if (!"计划".equals(m.getBizStatus())) {
            throw new BusinessException("仅计划状态的明细可开工");
        }
        WageGroup group = groupMapper.selectById(req.getGroupId());
        if (group == null) throw new BusinessException("请选择有效的加工组别");

        LocalDateTime now = LocalDateTime.now();
        m.setGroupId(group.getId());
        m.setBizStatus("开工");
        m.setStartTime(now);
        m.setSettleStatus("已提交");

        // 关键：processName 兜底 —— parseProcessName 没解析出来时，用加工组的默认工序
        if (m.getProcessName() == null || m.getProcessName().isEmpty()) {
            m.setProcessName(group.getDefaultProcess());
        }
        materialMapper.updateById(m);

        // 开工确认留痕
        WageOrder order = orderMapper.selectByTaskNo(m.getTaskNo());
        WageConfirm confirm = new WageConfirm();
        confirm.setTaskNo(m.getTaskNo());
        confirm.setMaterialCode(m.getMaterialCode());
        confirm.setGroupId(group.getId());
        confirm.setProcessName(m.getProcessName() != null ? m.getProcessName() : group.getDefaultProcess());
        confirm.setConfirmQty(m.getQty());
        confirm.setErpQty(m.getQty());
        confirm.setConfirmerNo(req.getConfirmerNo());
        confirm.setConfirmTime(now);
        confirm.setConfirmStatus("已确认");
        confirmMapper.insert(confirm);

        // 自动重算计件明细
        try {
            String operator = req.getConfirmerName() == null ? req.getConfirmerNo() : req.getConfirmerName();
            int n = payStatService.recalc(operator == null ? "开工自动" : operator);
            log.info("[工资核算] 开工自动重算计件明细：生成 {} 条（物料明细 id={}）", n, materialId);
        } catch (Exception e) {
            log.warn("[工资核算] 开工自动重算计件明细失败：{}", e.getMessage());
        }
    }

    /** 完工：按明细 ID */
    @Transactional(rollbackFor = Exception.class)
    public void doneMaterial(Long materialId, WageOrderDoneRequest req) {
        WageMaterial m = mustGetMaterial(materialId);
        if (!"开工".equals(m.getBizStatus())) {
            throw new BusinessException("仅开工状态的明细可确认完工");
        }
        LocalDateTime now = LocalDateTime.now();
        m.setBizStatus("完工");
        m.setFinishUser(req.getFinishUser());
        m.setFinishTime(now);
        m.setStockInQty(req.getStockInQty());
        m.setFinishQty(req.getStockInQty());
        materialMapper.updateById(m);

        try {
            int n = payStatService.recalc(req.getFinishUser() == null ? "完工自动" : req.getFinishUser());
            log.info("[工资核算] 完工自动重算计件明细：生成 {} 条（物料明细 id={}）", n, materialId);
        } catch (Exception e) {
            log.warn("[工资核算] 完工自动重算计件明细失败：{}", e.getMessage());
        }
    }

    /** 改组别：按明细 ID */
    @Transactional(rollbackFor = Exception.class)
    public void changeGroupMaterial(Long materialId, Long groupId) {
        WageMaterial m = mustGetMaterial(materialId);
        if ("完工".equals(m.getBizStatus())) {
            throw new BusinessException("已完工明细不允许修改组别");
        }
        if (groupMapper.selectById(groupId) == null) {
            throw new BusinessException("请选择有效的加工组别");
        }
        m.setGroupId(groupId);
        materialMapper.updateById(m);
    }

    /** 删除：按明细 ID */
    @Transactional(rollbackFor = Exception.class)
    public void removeMaterial(Long materialId) {
        WageMaterial m = mustGetMaterial(materialId);
        materialMapper.deleteById(m.getId());
    }

    // ------------------------------------------------------------
    // 兼容旧接口（按 task_no 批量操作 → 作用到该 task_no 下所有明细）
    // ------------------------------------------------------------

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id, WageOrderStartRequest req) {
        // 兼容：选中单据头 id 时，对该 task_no 下所有"计划"状态明细批量开工
        WageOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("任务单不存在或已删除");

        List<WageMaterial> plans = materialMapper.selectList(
                new LambdaQueryWrapper<WageMaterial>()
                        .eq(WageMaterial::getTaskNo, order.getTaskNo())
                        .eq(WageMaterial::getBizStatus, "计划")
                        .eq(WageMaterial::getDeleted, 0));
        if (plans.isEmpty()) throw new BusinessException("该任务单下无可开工明细");

        for (WageMaterial m : plans) {
            startMaterial(m.getId(), req);
        }
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void done(Long id, WageOrderDoneRequest req) {
        WageOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("任务单不存在或已删除");

        List<WageMaterial> running = materialMapper.selectList(
                new LambdaQueryWrapper<WageMaterial>()
                        .eq(WageMaterial::getTaskNo, order.getTaskNo())
                        .eq(WageMaterial::getBizStatus, "开工")
                        .eq(WageMaterial::getDeleted, 0));
        if (running.isEmpty()) throw new BusinessException("该任务单下无已开工明细");

        for (WageMaterial m : running) {
            doneMaterial(m.getId(), req);
        }
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void changeGroup(Long id, Long groupId) {
        WageOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("任务单不存在或已删除");

        List<WageMaterial> list = materialMapper.selectList(
                new LambdaQueryWrapper<WageMaterial>()
                        .eq(WageMaterial::getTaskNo, order.getTaskNo())
                        .ne(WageMaterial::getBizStatus, "完工")
                        .eq(WageMaterial::getDeleted, 0));
        if (list.isEmpty()) throw new BusinessException("该任务单下无可改组别明细");

        for (WageMaterial m : list) {
            changeGroupMaterial(m.getId(), groupId);
        }
    }

    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        WageOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("任务单不存在或已删除");

        materialMapper.delete(new LambdaQueryWrapper<WageMaterial>()
                .eq(WageMaterial::getTaskNo, order.getTaskNo()));
        orderMapper.deleteById(id);
    }

    // ------------------------------------------------------------
    // 金蝶同步辅助
    // ------------------------------------------------------------

    private void pushMaterialsFromKingdee(WageOrder order) {
        try {
            String materialId = order.getMaterialCode();
            if (materialId == null || materialId.isEmpty()) return;
            KingdeeService.LoginResult session = kingdeeService.getSession();
            List<com.enterprise.brain.task.entity.MesMaterialDetail> bom =
                    kingdeeService.queryBom(order.getTaskNo(), materialId, session);
            for (com.enterprise.brain.task.entity.MesMaterialDetail d : bom) {
                if (d.getMaterialCode() == null) continue;
                String name = d.getMaterialName();
                if (name == null || (!name.contains("丝印") && !name.contains("移印"))) continue;
                if (materialMapper.countByTaskAndMaterial(order.getTaskNo(), d.getMaterialCode()) > 0) continue;
                WageMaterial m = new WageMaterial();
                m.setTaskNo(order.getTaskNo());
                m.setFeedNo(order.getFeedNo());
                m.setBizStatus("开工");
                m.setMaterialCode(d.getMaterialCode());
                m.setMaterialName(d.getMaterialName());
                m.setUnit(d.getUnit());
                m.setQty(d.getRequiredQty() == null ? BigDecimal.ZERO : new BigDecimal(d.getRequiredQty()));
                m.setProcessName(parseProcessName(d.getMaterialName()));
                m.setSettleStatus("已提交");
                materialMapper.insert(m);
            }
        } catch (Exception e) {
            log.warn("[工资核算] 任务单 {} 金蝶用料清单下发失败：{}", order.getTaskNo(), e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int savePpbomRows(List<WagePpbomRowVO> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        int added = 0, skipped = 0;
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
            // 从已有 wage_material 中取该 task_no 现有明细的 bizStatus（如第一个，默认计划）
            String bizStatus = "计划";
            List<WageMaterial> first = materialMapper.selectList(
                    new LambdaQueryWrapper<WageMaterial>()
                            .eq(WageMaterial::getTaskNo, taskNo)
                            .eq(WageMaterial::getDeleted, 0));
            WageMaterial exist = first.isEmpty() ? null : first.get(0);
            if (exist != null && exist.getBizStatus() != null) bizStatus = exist.getBizStatus();

            WageMaterial m = new WageMaterial();
            m.setTaskNo(taskNo);
            m.setFeedNo(row.getPpbomBillNo());
            m.setBizStatus(bizStatus);
            m.setMaterialCode(materialCode);
            m.setMaterialName(row.getMaterialName());
            m.setUnit(row.getUnitName());
            m.setQty(row.getNeedQty() == null ? BigDecimal.ZERO : row.getNeedQty());
            m.setProcessName(parseProcessName(row.getMaterialName()));
            m.setSettleStatus("待结算");
            materialMapper.insert(m);
            added++;
        }
        log.info("[工资核算] 金蝶查询结果保存：新增 {}，跳过 {}", added, skipped);
        return added;
    }

    @Transactional(rollbackFor = Exception.class)
    public int syncFromMaterials() {
        List<String> materialTaskNos = materialMapper.selectList(
                new LambdaQueryWrapper<WageMaterial>()
                        .select(WageMaterial::getTaskNo)
                        .isNotNull(WageMaterial::getTaskNo))
                .stream()
                .map(WageMaterial::getTaskNo)
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .toList();

        if (materialTaskNos.isEmpty()) return 0;

        List<WageMoRowVO> kdOrders = kingdeeWageService.queryMoByBillNos(materialTaskNos);
        if (kdOrders.isEmpty()) return 0;

        int added = 0, updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (WageMoRowVO kd : kdOrders) {
            String taskNo = kd.getMoBillNo();
            if (taskNo == null || taskNo.isEmpty()) continue;
            WageOrder existing = orderMapper.selectByTaskNo(taskNo);
            if (existing != null) {
                existing.setProductName(kd.getProductName());
                existing.setMaterialCode(kd.getProductCode());
                existing.setProductQty(kd.getQty());
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
                o.setWorkshop(kd.getWorkshop());
                o.setSyncTime(now);
                orderMapper.insert(o);
                added++;
            }
        }
        log.info("[工资核算] syncFromMaterials：新增 {} / 更新 {}", added, updated);
        return added;
    }

    // ------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------

    private WageMaterial mustGetMaterial(Long id) {
        WageMaterial m = materialMapper.selectById(id);
        if (m == null) throw new BusinessException("用料明细不存在或已删除");
        return m;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 从物料名称解析工序 */
    private static String parseProcessName(String materialName) {
        if (materialName == null) return null;
        if (materialName.contains("丝印")) return "丝印";
        if (materialName.contains("移印")) return "移印";
        return null;
    }
}
