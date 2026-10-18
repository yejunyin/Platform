package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.entity.MesMaterialDetail;
import com.enterprise.brain.task.entity.MesProductOrder;
import com.enterprise.brain.task.mapper.MesProductOrderMapper;
import com.enterprise.brain.task.service.KingdeeService;
import com.enterprise.brain.task.wage.dto.PageVO;
import com.enterprise.brain.task.wage.dto.WageOrderDoneRequest;
import com.enterprise.brain.task.wage.dto.WageOrderStartRequest;
import com.enterprise.brain.task.wage.dto.WageOrderVO;
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
            o.setSubMaterialCode(mes.getMaterialid());
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
        confirm.setSubMaterialCode(order.getSubMaterialCode());
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
        String materialId = order.getSubMaterialCode();
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
}
