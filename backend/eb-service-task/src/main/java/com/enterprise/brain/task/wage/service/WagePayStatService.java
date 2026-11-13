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

    public List<WagePayStat> list(String month, Long groupId, String keyword, String reviewStatus) {
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) kw = null;
        String rs = trimNull(reviewStatus);
        if (rs != null && !"reviewed".equals(rs) && !"unreviewed".equals(rs)) rs = null;
        return payStatMapper.selectStatList(trimNull(month), groupId, kw, rs);
    }

    public List<String> months() {
        return payStatMapper.selectDistinctMonths();
    }

    /**
     * 全量重新计算（手动点"重新计算"按钮时调用）
     * <p>清除所有已开工/完工工单的 paystat 行，全量重建</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalc(String operator) {
        return doRecalc(operator, null);
    }

    /**
     * 按工单精确重新计算（开工/完工/改组别 时调用）
     * <p>只清除并重建指定 task_no 的 paystat，不影响其他工单的数据</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalcForTask(String operator, String taskNo) {
        return doRecalc(operator, taskNo);
    }

    /**
     * 核心重算逻辑
     *
     * @param operator 操作人
     * @param taskNo   指定工单（null = 全量，非空 = 只处理这一张）
     */
    private int doRecalc(String operator, String taskNo) {
        // ===== Step 1: 查询 wage_material（只算"完工"的，开工中的不算工资） =====
        LambdaQueryWrapper<WageMaterial> wrapper = new LambdaQueryWrapper<WageMaterial>()
                .eq(WageMaterial::getBizStatus, "完工")
                .isNotNull(WageMaterial::getStartTime)
                .isNotNull(WageMaterial::getGroupId)
                .eq(WageMaterial::getDeleted, 0);
        if (taskNo != null && !taskNo.isEmpty()) {
            wrapper.eq(WageMaterial::getTaskNo, taskNo);
        }
        List<WageMaterial> materials = materialMapper.selectList(wrapper);

        if (materials.isEmpty()) {
            log.warn("[工资核算] recalc{}：没有符合条件的用料明细（要求 biz_status=开工/完工 + start_time 非空 + group_id 非空）",
                    taskNo == null ? "" : "(taskNo=" + taskNo + ")");
            return 0;
        }

        // ===== Step 2: 按 task_no 分组 =====
        Map<String, List<WageMaterial>> byTask = new HashMap<>();
        for (WageMaterial m : materials) {
            byTask.computeIfAbsent(m.getTaskNo(), k -> new ArrayList<>()).add(m);
        }
        log.info("[工资核算] recalc{} 命中用料明细 {} 条，涉及工单 {} 张（操作人 {}）",
                taskNo == null ? "" : "(taskNo=" + taskNo + ")",
                materials.size(), byTask.size(), operator);

        // ===== Step 3: 按工单精确清除旧 paystat =====
        // Bug 根因修复：之前全局 deleteUnreviewed() 或全量重建会误伤其他工单的数据
        // 现在：只清除本次涉及的 task_no 的 paystat，其他工单数据一律不动
        int clearedTotal = 0;
        for (String tn : byTask.keySet()) {
            int cleared = payStatMapper.deleteAllByTaskNo(tn);
            clearedTotal += cleared;
            if (cleared > 0 || taskNo != null) {
                log.info("[工资核算] recalc：工单 {} 清除旧明细 {} 条", tn, cleared);
            }
        }
        log.info("[工资核算] recalc：本次清除旧明细共 {} 条，准备重建", clearedTotal);

        // ===== Step 4: 逐工单重建 paystat =====
        Map<Long, WageGroup> groupCache = new HashMap<>();
        Map<Long, List<WageStaffVO>> memberCache = new HashMap<>();

        int skippedNoPrice = 0, skippedNoMember = 0, skippedNoCoef = 0, okTask = 0;
        List<WagePayStat> toInsert = new ArrayList<>();

        for (Map.Entry<String, List<WageMaterial>> entry : byTask.entrySet()) {
            String tn = entry.getKey();
            List<WageMaterial> items = entry.getValue();

            // 4.1 逐明细算单价 & groupAmount
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
                            tn, m.getId(), m.getProcessName());
                }
            }
            if (groupAmount.signum() <= 0) {
                skippedNoPrice++;
                log.warn("[工资核算] [SKIP] 工单 {} 跳过：全部 {} 条子项无有效单价（涉及工序: {}）",
                        tn, items.size(), noPriceProcs);
                continue;
            }

            // 4.2 取组员
            Long groupId = items.get(0).getGroupId();
            WageGroup group = groupCache.computeIfAbsent(groupId, groupMapper::selectById);
            String groupName = group == null ? null : group.getGroupName();

            List<WageStaffVO> members = memberCache.computeIfAbsent(groupId, staffExtMapper::selectActiveMembers);
            if (members.isEmpty()) {
                skippedNoMember++;
                log.warn("[工资核算] [SKIP] 工单 {} 跳过：加工组 id={}({}) 无在职成员（请检查 wage_staff_ext 是否已配置该组成员）",
                        tn, groupId, groupName);
                continue;
            }

            // 4.3 计算系数和
            BigDecimal coefSum = BigDecimal.ZERO;
            for (WageStaffVO mem : members) {
                if (mem.getCoef() != null) coefSum = coefSum.add(mem.getCoef());
            }
            if (coefSum.signum() <= 0) {
                skippedNoCoef++;
                log.warn("[工资核算] [SKIP] 工单 {} 跳过：加工组 id={}({}) 成员系数和为 0（共 {} 人）",
                        tn, groupId, groupName, members.size());
                continue;
            }

            // 4.4 计算分摊 + 生成 paystat 行
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
                    ps.setTaskNo(tn);
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
                    tn, groupName, groupId, members.size(), groupAmount,
                    members.size() * calcs.size());
        }

        // ===== Step 5: 批量插入 =====
        for (WagePayStat ps : toInsert) {
            payStatMapper.insert(ps);
        }
        log.info("[工资核算] recalc 完成（{}）：生成 {} 行（成功 {} 工单，跳过 {} = 无单价/无成员/系数0）",
                taskNo == null ? "全量" : "taskNo=" + taskNo,
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
