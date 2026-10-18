package com.enterprise.brain.task.wage.service;

import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.wage.dto.WageAuditRequest;
import com.enterprise.brain.task.wage.dto.WageSummaryVO;
import com.enterprise.brain.task.wage.entity.WagePayStat;
import com.enterprise.brain.task.wage.entity.WageRecord;
import com.enterprise.brain.task.wage.mapper.WagePayStatMapper;
import com.enterprise.brain.task.wage.mapper.WageRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工资管理：paystat 按 工资月份 + 工号 实时汇总；审核时按需生成 wage_record
 */
@Service
public class WageRecordService {

    @Resource
    private WagePayStatMapper payStatMapper;
    @Resource
    private WageRecordMapper recordMapper;

    /** 工资单号规则 WG{YYYYMM}-{工号} */
    public static String wageNoOf(String month, String staffNo) {
        return "WG" + month.replace("-", "") + "-" + staffNo;
    }

    public List<WageSummaryVO> summary(String month, String keyword, String auditStatus) {
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }
        String m = month == null || month.trim().isEmpty() ? null : month.trim();
        String aud = auditStatus == null || auditStatus.trim().isEmpty() ? null : auditStatus.trim();

        List<WagePayStat> rows = payStatMapper.selectStatList(m, null, null);

        // 按 月份 + 工号 归并，保持月份插入顺序
        Map<String, WageSummaryVO> map = new LinkedHashMap<>();
        for (WagePayStat r : rows) {
            String key = r.getWageMonth() + "|" + r.getStaffNo();
            WageSummaryVO vo = map.computeIfAbsent(key, k -> {
                WageSummaryVO v = new WageSummaryVO();
                v.setWageMonth(r.getWageMonth());
                v.setStaffNo(r.getStaffNo());
                v.setStaffName(r.getStaffName());
                v.setGroupId(r.getGroupId());
                v.setGroupName(r.getGroupName());
                v.setJobType(r.getJobType());
                v.setDetailCount(0);
                v.setPieceAmount(BigDecimal.ZERO);
                return v;
            });
            vo.setDetailCount(vo.getDetailCount() + 1);
            if (r.getAmount() != null) {
                vo.setPieceAmount(vo.getPieceAmount().add(r.getAmount()));
            }
            if ((vo.getStaffName() == null || vo.getStaffName().isEmpty()) && r.getStaffName() != null) {
                vo.setStaffName(r.getStaffName());
            }
            if (vo.getGroupId() == null && r.getGroupId() != null) {
                vo.setGroupId(r.getGroupId());
                vo.setGroupName(r.getGroupName());
                vo.setJobType(r.getJobType());
            }
        }

        List<WageSummaryVO> list = new ArrayList<>(map.values());
        for (WageSummaryVO vo : list) {
            vo.setWageNo(wageNoOf(vo.getWageMonth(), vo.getStaffNo()));
            WageRecord rec = recordMapper.selectByWageNo(vo.getWageNo());
            vo.setAuditStatus(rec == null ? "未生成" : rec.getAuditStatus());
        }

        List<WageSummaryVO> filtered = new ArrayList<>();
        for (WageSummaryVO vo : list) {
            if (kw != null && !(contains(vo.getStaffName(), kw) || contains(vo.getStaffNo(), kw)
                    || contains(vo.getGroupName(), kw) || contains(vo.getJobType(), kw))) {
                continue;
            }
            if (aud != null && !aud.equals(vo.getAuditStatus())) {
                continue;
            }
            filtered.add(vo);
        }
        filtered.sort(Comparator.comparing(WageSummaryVO::getWageMonth).reversed()
                .thenComparing(WageSummaryVO::getStaffNo));
        return filtered;
    }

    /**
     * 单条审核：未生成时先补 wage_record（待审核），再通过/驳回
     */
    @Transactional(rollbackFor = Exception.class)
    public WageRecord auditOne(WageAuditRequest req) {
        if (req.getMonth() == null || req.getStaffNo() == null) {
            throw new BusinessException("缺少月份或工号");
        }
        WageSummaryVO vo = summary(req.getMonth(), req.getStaffNo(), null).stream()
                .filter(v -> req.getMonth().equals(v.getWageMonth()) && req.getStaffNo().equals(v.getStaffNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("该月份/工号暂无计件明细，无法审核"));
        WageRecord rec = ensureRecord(vo);
        boolean approved = !Boolean.FALSE.equals(req.getApproved());
        if (approved) {
            rec.setAuditStatus("已审核");
            rec.setAuditor(req.getAuditor());
            rec.setSettleDate(LocalDate.now());
        } else {
            rec.setAuditStatus("已驳回");
            rec.setAuditor(req.getAuditor());
        }
        recordMapper.updateById(rec);
        return rec;
    }

    /**
     * 批量审核通过（当前筛选范围内非「已审核」汇总行）
     * @return 审核条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int auditBatch(WageAuditRequest req) {
        List<WageSummaryVO> rows = summary(req.getMonth(), req.getKeyword(), req.getAuditStatus());
        int n = 0;
        for (WageSummaryVO vo : rows) {
            if ("已审核".equals(vo.getAuditStatus())) {
                continue;
            }
            WageRecord rec = ensureRecord(vo);
            rec.setAuditStatus("已审核");
            rec.setAuditor(req.getAuditor());
            rec.setSettleDate(LocalDate.now());
            recordMapper.updateById(rec);
            n++;
        }
        return n;
    }

    /**
     * 审核前确保工资表记录存在；金额始终以实时汇总为准
     */
    private WageRecord ensureRecord(WageSummaryVO vo) {
        WageRecord rec = recordMapper.selectByWageNo(vo.getWageNo());
        if (rec != null) {
            rec.setStaffName(vo.getStaffName());
            rec.setGroupId(vo.getGroupId());
            rec.setGroupName(vo.getGroupName());
            rec.setPieceAmount(vo.getPieceAmount());
            return rec;
        }
        rec = new WageRecord();
        rec.setWageNo(vo.getWageNo());
        rec.setStaffName(vo.getStaffName());
        rec.setStaffNo(vo.getStaffNo());
        rec.setGroupId(vo.getGroupId());
        rec.setGroupName(vo.getGroupName());
        rec.setSettlePeriod(vo.getWageMonth());
        rec.setPieceAmount(vo.getPieceAmount());
        rec.setAuditStatus("待审核");
        recordMapper.insert(rec);
        return rec;
    }

    private static boolean contains(String s, String kw) {
        return s != null && s.contains(kw);
    }
}
