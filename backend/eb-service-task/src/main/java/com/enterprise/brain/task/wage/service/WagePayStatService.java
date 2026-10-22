package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.wage.dto.WagePayStatReviewRequest;
import com.enterprise.brain.task.wage.dto.WageBatchMonthRequest;
import com.enterprise.brain.task.wage.dto.WageStaffVO;
import com.enterprise.brain.task.wage.entity.WageGroup;
import com.enterprise.brain.task.wage.entity.WageMaterial;
import com.enterprise.brain.task.wage.entity.WageOrder;
import com.enterprise.brain.task.wage.entity.WagePayStat;
import com.enterprise.brain.task.wage.entity.WagePrice;
import com.enterprise.brain.task.wage.mapper.WageGroupMapper;
import com.enterprise.brain.task.wage.mapper.WageMaterialMapper;
import com.enterprise.brain.task.wage.mapper.WageOrderMapper;
import com.enterprise.brain.task.wage.mapper.WagePayStatMapper;
import com.enterprise.brain.task.wage.mapper.WageStaffExtMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计件复核（工资统计明细）：
 * 核算单元 = 生产任务单 × 用料明细 × 成员，按开工月份归集，按组内系数分摊。
 */
@Slf4j
@Service
public class WagePayStatService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Resource
    private WagePayStatMapper payStatMapper;
    @Resource
    private WageOrderMapper orderMapper;
    @Resource
    private WageMaterialMapper materialMapper;
    @Resource
    private WageStaffExtMapper staffExtMapper;
    @Resource
    private WageGroupMapper groupMapper;
    @Resource
    private WagePriceService priceService;

    public List<WagePayStat> list(String month, Long groupId, String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }
        return payStatMapper.selectStatList(trimNull(month), groupId, kw);
    }

    public List<String> months() {
        return payStatMapper.selectDistinctMonths();
    }

    /**
     * 重新计算：物理清空旧明细，按当前订单/清单/单价/成员重新生成
     * @return 生成明细条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalc(String operator) {
        payStatMapper.deleteAllPhysical();

        // 业务状态∈{开工,完工}、有开工时间、有加工组别的工单
        List<WageOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<WageOrder>()
                .in(WageOrder::getBizStatus, "开工", "完工")
                .isNotNull(WageOrder::getStartTime)
                .isNotNull(WageOrder::getGroupId));

        // 组信息缓存
        Map<Long, WageGroup> groupCache = new HashMap<>();
        // 组内在职成员缓存
        Map<Long, List<WageStaffVO>> memberCache = new HashMap<>();

        List<WagePayStat> toInsert = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (WageOrder order : orders) {
            String month = order.getStartTime().format(MONTH_FMT);

            // 该工单全部用料子项（按任务单号，不能按单个物料代码过滤）
            List<WageMaterial> items = materialMapper.selectMaterials(order.getTaskNo(), null, null);
            if (items.isEmpty()) {
                continue;
            }

            // 逐子项算明细金额与工单组级金额
            BigDecimal groupAmount = BigDecimal.ZERO;
            List<ItemCalc> calcs = new ArrayList<>();
            for (WageMaterial m : items) {
                WagePrice price = priceService.matchPrice(m.getMaterialCode(), order.getProcessName());
                BigDecimal subPrice = price == null ? BigDecimal.ZERO
                        : price.getUnitPrice().setScale(4, RoundingMode.HALF_UP);
                BigDecimal subQty = m.getQty() == null ? BigDecimal.ZERO : m.getQty();
                BigDecimal detailAmount = subQty.multiply(subPrice).setScale(2, RoundingMode.HALF_UP);
                groupAmount = groupAmount.add(detailAmount);
                calcs.add(new ItemCalc(m, subPrice, detailAmount));
            }
            if (groupAmount.signum() <= 0) {
                // 无有效单价则不生成分摊明细（金额为 0 无核算意义）
                continue;
            }

            List<WageStaffVO> members = memberCache.computeIfAbsent(order.getGroupId(),
                    staffExtMapper::selectActiveMembers);
            if (members.isEmpty()) {
                continue;
            }
            BigDecimal coefSum = BigDecimal.ZERO;
            for (WageStaffVO mem : members) {
                if (mem.getCoef() != null) {
                    coefSum = coefSum.add(mem.getCoef());
                }
            }
            if (coefSum.signum() <= 0) {
                continue;
            }
            WageGroup group = groupCache.computeIfAbsent(order.getGroupId(), groupMapper::selectById);
            String groupName = group == null ? null : group.getGroupName();

            for (WageStaffVO mem : members) {
                BigDecimal coef = mem.getCoef() == null ? BigDecimal.ZERO : mem.getCoef();
                BigDecimal ratio = coef.divide(coefSum, 4, RoundingMode.HALF_UP);
                // 同一工单同一成员，每条明细的个人金额相同（按工单组级总额分摊）
                BigDecimal personalAmount = groupAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);

                for (ItemCalc c : calcs) {
                    WagePayStat ps = new WagePayStat();
                    ps.setWageMonth(month);
                    ps.setTaskNo(order.getTaskNo());
                    ps.setGroupId(order.getGroupId());
                    ps.setGroupName(groupName);
                    ps.setStaffName(mem.getRealName());
                    ps.setStaffNo(mem.getUsername());
                    ps.setJobType(mem.getJobType());
                    ps.setProductName(order.getProductName());
                    ps.setUnit(c.item.getUnit());
                    ps.setWorkPrice(c.price);
                    ps.setQty(c.item.getQty());
                    ps.setAmount(personalAmount);
                    ps.setStartTime(order.getStartTime());
                    ps.setProcessName(order.getProcessName());
                    ps.setMaterialCode(c.item.getMaterialCode());
                    ps.setSubMaterialName(c.item.getMaterialName());
                    ps.setSubQty(c.item.getQty());
                    ps.setSubPrice(c.price);
                    ps.setDetailAmount(c.detailAmount);
                    ps.setGroupAmount(groupAmount);
                    ps.setCoef(coef);
                    ps.setShareRatio(ratio);
                    ps.setEntryUser(operator);
                    toInsert.add(ps);
                }
            }
        }

        for (WagePayStat ps : toInsert) {
            payStatMapper.insert(ps);
        }
        log.info("[工资核算] 计件明细重新计算完成，生成 {} 行（工单 {} 张，操作人 {}）",
                toInsert.size(), orders.size(), operator);
        return toInsert.size();
    }

    /**
     * 单条复核：改工资月份/个人金额/备注，记录复核人与时间（本地墙钟）
     */
    @Transactional(rollbackFor = Exception.class)
    public WagePayStat review(Long id, WagePayStatReviewRequest req) {
        WagePayStat ps = payStatMapper.selectById(id);
        if (ps == null) {
            throw new BusinessException("明细不存在或已删除");
        }
        String month = req.getWageMonth() == null ? null : req.getWageMonth().trim();
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("工资月份格式应为 YYYY-MM");
        }
        ps.setWageMonth(month);
        if (req.getAmount() != null) {
            ps.setAmount(req.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
        ps.setRemark(req.getRemark());
        ps.setReviewer(req.getReviewer());
        ps.setReviewTime(LocalDateTime.now());
        payStatMapper.updateById(ps);
        return ps;
    }

    /**
     * 批量改写工资月份（勾选行跨页保留由前端传 id 列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateMonth(WageBatchMonthRequest req) {
        String month = req.getWageMonth().trim();
        if (!month.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("工资月份格式应为 YYYY-MM");
        }
        int n = 0;
        for (Long id : req.getIds()) {
            WagePayStat ps = payStatMapper.selectById(id);
            if (ps == null) {
                continue;
            }
            ps.setWageMonth(month);
            if (req.getRemark() != null && !req.getRemark().isEmpty()) {
                ps.setRemark(req.getRemark());
            }
            payStatMapper.updateById(ps);
            n++;
        }
        return n;
    }

    private static String trimNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 单子项计算中间结果 */
    private record ItemCalc(WageMaterial item, BigDecimal price, BigDecimal detailAmount) {
    }
}
