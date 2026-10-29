package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.wage.dto.WageBatchMonthRequest;
import com.enterprise.brain.task.wage.dto.WagePayStatReviewRequest;
import com.enterprise.brain.task.wage.dto.WageStaffVO;
import com.enterprise.brain.task.wage.entity.WageGroup;
import com.enterprise.brain.task.wage.entity.WageMaterial;
import com.enterprise.brain.task.wage.entity.WagePayStat;
import com.enterprise.brain.task.wage.entity.WagePrice;
import com.enterprise.brain.task.wage.mapper.WageGroupMapper;
import com.enterprise.brain.task.wage.mapper.WageMaterialMapper;
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
 * 核算单元 = 用料明细 × 成员，按开工月份归集，按组内系数分摊。
 */
@Slf4j
@Service
public class WagePayStatService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Resource
    private WagePayStatMapper payStatMapper;
    @Resource
    private WageMaterialMapper materialMapper;
    @Resource
    private WageGroupMapper groupMapper;
    @Resource
    private WageStaffExtMapper staffExtMapper;
    @Resource
    private WagePriceService priceService;

    public List<WagePayStat> list(String month, Long groupId, String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) kw = null;
        return payStatMapper.selectStatList(trimNull(month), groupId, kw);
    }

    public List<String> months() {
        return payStatMapper.selectDistinctMonths();
    }

    /**
     * 重新计算：物理清空旧明细，按 wage_material 中 开工/完工 状态的明细重新生成
     * @return 生成明细条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalc(String operator) {
        payStatMapper.deleteAllPhysical();

        // 从 wage_material 取所有"开工/完工"且有 startTime + groupId 的明细
        List<WageMaterial> materials = materialMapper.selectList(
                new LambdaQueryWrapper<WageMaterial>()
                        .in(WageMaterial::getBizStatus, "开工", "完工")
                        .isNotNull(WageMaterial::getStartTime)
                        .isNotNull(WageMaterial::getGroupId)
                        .eq(WageMaterial::getDeleted, 0));

        if (materials.isEmpty()) {
            log.warn("[工资核算] recalc：没有符合条件的用料明细（要求 biz_status=开工/完工 + start_time 非空 + group_id 非空）");
            return 0;
        }

        // 按 task_no 分组（同一工单的多条明细一起算 groupAmount）
        Map<String, List<WageMaterial>> byTask = new HashMap<>();
        for (WageMaterial m : materials) {
            byTask.computeIfAbsent(m.getTaskNo(), k -> new ArrayList<>()).add(m);
        }
        log.info("[工资核算] recalc 命中用料明细 {} 条，涉及工单 {} 张（操作人 {}）",
                materials.size(), byTask.size(), operator);

        // 缓存
        Map<Long, WageGroup> groupCache = new HashMap<>();
        Map<Long, List<WageStaffVO>> memberCache = new HashMap<>();

        // 汇总计数器
        int skippedNoPrice = 0, skippedNoMember = 0, skippedNoCoef = 0, okTask = 0;

        List<WagePayStat> toInsert = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, List<WageMaterial>> entry : byTask.entrySet()) {
            String taskNo = entry.getKey();
            List<WageMaterial> items = entry.getValue();

            // 逐明细算单价 & groupAmount
            BigDecimal groupAmount = BigDecimal.ZERO;
            List<ItemCalc> calcs = new ArrayList<>();
            StringBuilder noPriceProcs = new StringBuilder();
            for (WageMaterial m : items) {
                WagePrice price = priceService.matchPrice(m.getProcessName());
                BigDecimal subPrice = price == null ? BigDecimal.ZERO
                        : price.getUnitPrice().setScale(4, RoundingMode.HALF_UP);
                BigDecimal subQty = m.getQty() == null ? BigDecimal.ZERO : m.getQty();
                BigDecimal detailAmount = subQty.multiply(subPrice).setScale(2, RoundingMode.HALF_UP);
                groupAmount = groupAmount.add(detailAmount);
                calcs.add(new ItemCalc(m, subPrice, detailAmount));
                if (price == null) {
                    if (noPriceProcs.length() > 0) noPriceProcs.append(",");
                    noPriceProcs.append(m.getProcessName());
                    log.warn("[工资核算] 工单 {} 用料明细 id={} 工序='{}' 未匹配到有效单价",
                            taskNo, m.getId(), m.getProcessName());
                }
            }
            if (groupAmount.signum() <= 0) {
                skippedNoPrice++;
                log.warn("[工资核算] [SKIP] 工单 {} 跳过：全部 {} 条子项无有效单价（涉及工序: {}）",
                        taskNo, items.size(), noPriceProcs);
                continue;
            }

            // 取第一条明细的 groupId 作为工单 group（同一工单组应该相同）
            Long groupId = items.get(0).getGroupId();
            WageGroup group = groupCache.computeIfAbsent(groupId, groupMapper::selectById);
            String groupName = group == null ? null : group.getGroupName();

            List<WageStaffVO> members = memberCache.computeIfAbsent(groupId, staffExtMapper::selectActiveMembers);
            if (members.isEmpty()) {
                skippedNoMember++;
                log.warn("[工资核算] [SKIP] 工单 {} 跳过：加工组 id={}({}) 无在职成员（请检查 wage_staff_ext 是否已配置该组成员）",
                        taskNo, groupId, groupName);
                continue;
            }
            BigDecimal coefSum = BigDecimal.ZERO;
            for (WageStaffVO mem : members) {
                if (mem.getCoef() != null) coefSum = coefSum.add(mem.getCoef());
            }
            if (coefSum.signum() <= 0) {
                skippedNoCoef++;
                log.warn("[工资核算] [SKIP] 工单 {} 跳过：加工组 id={}({}) 成员系数和为 0（共 {} 人）",
                        taskNo, groupId, groupName, members.size());
                continue;
            }

            // 同一工单，用第一条明细的 startTime 作为月份归属
            LocalDateTime startTime = items.get(0).getStartTime();
            String month = startTime.format(MONTH_FMT);

            for (WageStaffVO mem : members) {
                BigDecimal coef = mem.getCoef() == null ? BigDecimal.ZERO : mem.getCoef();
                BigDecimal ratio = coef.divide(coefSum, 4, RoundingMode.HALF_UP);
                BigDecimal personalAmount = groupAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);

                for (ItemCalc c : calcs) {
                    WageMaterial m = c.item;
                    WagePayStat ps = new WagePayStat();
                    ps.setWageMonth(month);
                    ps.setTaskNo(taskNo);
                    ps.setGroupId(groupId);
                    ps.setGroupName(groupName);
                    ps.setStaffName(mem.getRealName());
                    ps.setStaffNo(mem.getUsername());
                    ps.setJobType(mem.getJobType());
                    ps.setUnit(m.getUnit());
                    ps.setWorkPrice(c.price);
                    ps.setQty(m.getQty());
                    ps.setAmount(personalAmount);
                    ps.setStartTime(m.getStartTime());
                    ps.setProcessName(m.getProcessName());
                    ps.setMaterialCode(m.getMaterialCode());
                    ps.setSubMaterialName(m.getMaterialName());
                    ps.setSubQty(m.getQty());
                    ps.setSubPrice(c.price);
                    ps.setDetailAmount(c.detailAmount);
                    ps.setGroupAmount(groupAmount);
                    ps.setCoef(coef);
                    ps.setShareRatio(ratio);
                    ps.setEntryUser(operator);
                    toInsert.add(ps);
                }
            }
            okTask++;
            log.info("[工资核算] [OK] 工单 {} 通过：组={}({}) 成员={}人 groupAmount={} 生成={}行",
                    taskNo, groupName, groupId, members.size(), groupAmount,
                    members.size() * calcs.size());
        }

        for (WagePayStat ps : toInsert) {
            payStatMapper.insert(ps);
        }
        log.info("[工资核算] recalc 完成：生成 {} 行（成功 {} 工单，跳过 {} = 无单价/无成员/系数0）",
                toInsert.size(), okTask,
                skippedNoPrice + skippedNoMember + skippedNoCoef);
        return toInsert.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public WagePayStat review(Long id, WagePayStatReviewRequest req) {
        WagePayStat ps = payStatMapper.selectById(id);
        if (ps == null) throw new BusinessException("明细不存在或已删除");
        String month = req.getWageMonth() == null ? null : req.getWageMonth().trim();
        if (month == null || !month.matches("\\d{4}-\\d{2}")) throw new BusinessException("工资月份格式应为 YYYY-MM");
        ps.setWageMonth(month);
        if (req.getAmount() != null) ps.setAmount(req.getAmount().setScale(2, RoundingMode.HALF_UP));
        ps.setRemark(req.getRemark());
        ps.setReviewer(req.getReviewer());
        ps.setReviewTime(LocalDateTime.now());
        payStatMapper.updateById(ps);
        return ps;
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateMonth(WageBatchMonthRequest req) {
        String month = req.getWageMonth().trim();
        if (!month.matches("\\d{4}-\\d{2}")) throw new BusinessException("工资月份格式应为 YYYY-MM");
        int n = 0;
        for (Long id : req.getIds()) {
            WagePayStat ps = payStatMapper.selectById(id);
            if (ps == null) continue;
            ps.setWageMonth(month);
            if (req.getRemark() != null && !req.getRemark().isEmpty()) ps.setRemark(req.getRemark());
            payStatMapper.updateById(ps);
            n++;
        }
        return n;
    }

    private static String trimNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record ItemCalc(WageMaterial item, BigDecimal price, BigDecimal detailAmount) {}
}
