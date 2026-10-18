package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.wage.dto.WageStaffVO;
import com.enterprise.brain.task.wage.entity.WageGroup;
import com.enterprise.brain.task.wage.mapper.WageGroupMapper;
import com.enterprise.brain.task.wage.mapper.WageStaffExtMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 人员小组维护（含组内系数和/组员数冗余回写）
 */
@Service
public class WageGroupService {

    @Resource
    private WageGroupMapper groupMapper;
    @Resource
    private WageStaffExtMapper staffExtMapper;

    public List<WageGroup> list() {
        return groupMapper.selectList(new LambdaQueryWrapper<WageGroup>().orderByAsc(WageGroup::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public WageGroup save(WageGroup group) {
        String name = group.getGroupName() == null ? null : group.getGroupName().trim();
        if (name == null || name.isEmpty()) {
            throw new BusinessException("请填写小组名称");
        }
        group.setGroupName(name);
        if (group.getId() == null) {
            Long dup = groupMapper.selectCount(new LambdaQueryWrapper<WageGroup>()
                    .eq(WageGroup::getGroupName, name));
            if (dup != null && dup > 0) {
                throw new BusinessException("小组名称已存在：" + name);
            }
            if (group.getCoefSum() == null) group.setCoefSum(BigDecimal.ZERO);
            if (group.getMemberCount() == null) group.setMemberCount(0);
            groupMapper.insert(group);
        } else {
            WageGroup old = groupMapper.selectById(group.getId());
            if (old == null) {
                throw new BusinessException("小组不存在或已删除");
            }
            Long dup = groupMapper.selectCount(new LambdaQueryWrapper<WageGroup>()
                    .eq(WageGroup::getGroupName, name)
                    .ne(WageGroup::getId, group.getId()));
            if (dup != null && dup > 0) {
                throw new BusinessException("小组名称已存在：" + name);
            }
            old.setGroupName(name);
            old.setLeaderNo(group.getLeaderNo());
            old.setDefaultProcess(group.getDefaultProcess());
            old.setRemark(group.getRemark());
            groupMapper.updateById(old);
            group = old;
        }
        recalcRollup(group.getId());
        return group;
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        WageGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new BusinessException("小组不存在或已删除");
        }
        List<WageStaffVO> members = staffExtMapper.selectActiveMembers(id);
        if (!members.isEmpty()) {
            throw new BusinessException("该小组仍有 " + members.size() + " 名在职成员，请先调整成员归属");
        }
        groupMapper.deleteById(id);
    }

    /**
     * 回写组内在职成员数与系数和
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalcRollup(Long groupId) {
        if (groupId == null) {
            return;
        }
        WageGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            return;
        }
        List<WageStaffVO> members = staffExtMapper.selectActiveMembers(groupId);
        BigDecimal sum = BigDecimal.ZERO;
        for (WageStaffVO m : members) {
            if (m.getCoef() != null) {
                sum = sum.add(m.getCoef());
            }
        }
        group.setMemberCount(members.size());
        group.setCoefSum(sum);
        groupMapper.updateById(group);
    }
}
