package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.entity.SysUser;
import com.enterprise.brain.task.mapper.SysUserMapper;
import com.enterprise.brain.task.wage.dto.WageStaffSaveRequest;
import com.enterprise.brain.task.wage.dto.WageStaffUpdateRequest;
import com.enterprise.brain.task.wage.dto.WageStaffVO;
import com.enterprise.brain.task.wage.entity.WageGroup;
import com.enterprise.brain.task.wage.entity.WageStaffExt;
import com.enterprise.brain.task.wage.mapper.WageGroupMapper;
import com.enterprise.brain.task.wage.mapper.WageStaffExtMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工工资资料：身份复用 sys_user，工资属性存 wage_staff_ext（1:1）
 */
@Service
public class WageStaffService {

    /** 新增员工默认密码（123456 的 BCrypt 密文，与质检人员维护一致） */
    private static final String DEFAULT_PASSWORD =
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    /** 系统管理员受保护，不允许在工资资料中删除 */
    private static final String PROTECTED_ADMIN = "admin";

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private WageStaffExtMapper staffExtMapper;
    @Resource
    private WageGroupMapper groupMapper;
    @Resource
    private WageGroupService groupService;

    public List<WageStaffVO> list(String keyword, Long groupId, Integer status) {
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }
        return staffExtMapper.selectStaffList(kw, groupId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public WageStaffVO add(WageStaffSaveRequest req) {
        String username = req.getUsername().trim();
        String realName = req.getRealName().trim();
        Long groupId = checkGroup(req.getGroupId());

        SysUser user = sysUserMapper.selectByUsername(username);
        if (user != null && (user.getDeleted() == null || user.getDeleted() == 0)) {
            throw new BusinessException("工号已存在：" + username);
        }
        if (user == null) {
            user = new SysUser();
            user.setId(sysUserMapper.selectMaxId() + 1);
            user.setUsername(username);
            user.setPassword(DEFAULT_PASSWORD);
            user.setRealName(realName);
            user.setStatus(1);
            user.setDeleted(0);
            sysUserMapper.insert(user);
        } else {
            // 同工号曾软删除：恢复复用
            sysUserMapper.restoreById(user.getId(), realName, null);
        }

        WageStaffExt ext = findExt(user.getId());
        boolean isNew = ext == null;
        if (isNew) {
            ext = new WageStaffExt();
            ext.setUserId(user.getId());
        }
        applyExt(ext, req.getJobType(), groupId, req.getCoef(), req.getHireDate());
        if (isNew) {
            staffExtMapper.insert(ext);
        } else {
            staffExtMapper.updateById(ext);
        }
        groupService.recalcRollup(groupId);
        return loadOne(user.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public WageStaffVO update(Long userId, WageStaffUpdateRequest req) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException("员工不存在或已离职删除");
        }
        Long oldGroup = null;
        WageStaffExt ext = findExt(userId);
        if (ext != null) {
            oldGroup = ext.getGroupId();
        }
        Long newGroup = checkGroup(req.getGroupId());

        user.setRealName(req.getRealName().trim());
        sysUserMapper.updateById(user);

        boolean isNew = ext == null;
        if (isNew) {
            ext = new WageStaffExt();
            ext.setUserId(userId);
        }
        applyExt(ext, req.getJobType(), newGroup, req.getCoef(), req.getHireDate());
        if (isNew) {
            staffExtMapper.insert(ext);
        } else {
            staffExtMapper.updateById(ext);
        }
        groupService.recalcRollup(newGroup);
        if (oldGroup != null && !oldGroup.equals(newGroup)) {
            groupService.recalcRollup(oldGroup);
        }
        return loadOne(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException("员工不存在或已删除");
        }
        if (PROTECTED_ADMIN.equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException("系统管理员账号不允许删除");
        }
        Long groupId = null;
        WageStaffExt ext = findExt(userId);
        if (ext != null) {
            groupId = ext.getGroupId();
            staffExtMapper.deleteById(ext.getId());
        }
        sysUserMapper.softDeleteById(userId);
        if (groupId != null) {
            groupService.recalcRollup(groupId);
        }
    }

    // ------------------------------------------------------------

    private WageStaffExt findExt(Long userId) {
        return staffExtMapper.selectOne(new LambdaQueryWrapper<WageStaffExt>()
                .eq(WageStaffExt::getUserId, userId));
    }

    private void applyExt(WageStaffExt ext, String jobType, Long groupId,
                          BigDecimal coef, java.time.LocalDate hireDate) {
        ext.setJobType((jobType == null || jobType.isEmpty()) ? "员工" : jobType.trim());
        ext.setGroupId(groupId);
        ext.setCoef(coef == null ? BigDecimal.ONE : coef);
        ext.setHireDate(hireDate);
    }

    private Long checkGroup(Long groupId) {
        if (groupId == null) {
            return null;
        }
        WageGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException("所选小组不存在");
        }
        return groupId;
    }

    private WageStaffVO loadOne(Long userId) {
        return staffExtMapper.selectStaffList(null, null, null).stream()
                .filter(v -> userId.equals(v.getUserId()))
                .findFirst()
                .orElse(null);
    }
}
