package com.enterprise.brain.task.service.impl;

import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.dto.request.QcStaffSaveRequest;
import com.enterprise.brain.task.dto.response.QcStaffVO;
import com.enterprise.brain.task.entity.SysUser;
import com.enterprise.brain.task.mapper.SysUserMapper;
import com.enterprise.brain.task.service.QcStaffService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 质检人员维护实现（直接操作 sys_user：username=工号，real_name=姓名）
 */
@Slf4j
@Service
public class QcStaffServiceImpl implements QcStaffService {

    /** 新增人员默认密码（123456 的 BCrypt 密文，与初始化脚本种子用户一致） */
    private static final String DEFAULT_PASSWORD =
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    /** 受保护账号：系统管理员不允许在质检人员维护中删除 */
    private static final String PROTECTED_ADMIN = "admin";

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public List<QcStaffVO> list(String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }
        return sysUserMapper.selectStaffList(kw).stream()
                .map(u -> new QcStaffVO(u.getId(), u.getUsername(), u.getRealName(),
                        trimToNull(u.getDingdingId())))
                .toList();
    }

    /** 去掉首尾空白；空白串归一为 null，便于数据库清空绑定 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QcStaffVO add(QcStaffSaveRequest request) {
        String username = request.getUsername().trim();
        String realName = request.getRealName().trim();
        String dingdingId = trimToNull(request.getDingdingId());

        SysUser exist = sysUserMapper.selectByUsername(username);
        if (exist != null) {
            if (exist.getDeleted() == null || exist.getDeleted() == 0) {
                throw new BusinessException("工号已存在：" + username);
            }
            // 同工号记录此前被软删除：恢复并更新姓名、钉钉ID，保证唯一约束可复用
            sysUserMapper.restoreById(exist.getId(), realName, dingdingId);
            log.info("[质检人员] 恢复已删除工号: id={}, username={}, realName={}, dingdingId={}",
                    exist.getId(), username, realName, dingdingId);
            return new QcStaffVO(exist.getId(), username, realName, dingdingId);
        }

        SysUser user = new SysUser();
        user.setId(sysUserMapper.selectMaxId() + 1);
        user.setUsername(username);
        user.setPassword(DEFAULT_PASSWORD);
        user.setRealName(realName);
        user.setDingdingId(dingdingId);
        user.setStatus(1);
        user.setDeleted(0);
        sysUserMapper.insert(user);
        log.info("[质检人员] 新增: id={}, username={}, realName={}, dingdingId={}",
                user.getId(), username, realName, dingdingId);
        return new QcStaffVO(user.getId(), username, realName, dingdingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException("人员不存在或已删除");
        }
        if (PROTECTED_ADMIN.equalsIgnoreCase(user.getUsername())) {
            throw new BusinessException("系统管理员账号不允许删除");
        }
        int rows = sysUserMapper.softDeleteById(id);
        if (rows == 0) {
            throw new BusinessException("删除失败，人员状态已变更，请刷新后重试");
        }
        log.info("[质检人员] 删除(软删除): id={}, username={}, realName={}",
                id, user.getUsername(), user.getRealName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QcStaffVO updateDingdingId(Long id, String dingdingId) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException("人员不存在或已删除");
        }
        String ddId = trimToNull(dingdingId);
        int rows = sysUserMapper.updateDingdingId(id, ddId);
        if (rows == 0) {
            throw new BusinessException("保存失败，人员状态已变更，请刷新后重试");
        }
        log.info("[质检人员] 维护钉钉ID: id={}, username={}, dingdingId={}",
                id, user.getUsername(), ddId);
        return new QcStaffVO(user.getId(), user.getUsername(), user.getRealName(), ddId);
    }
}
