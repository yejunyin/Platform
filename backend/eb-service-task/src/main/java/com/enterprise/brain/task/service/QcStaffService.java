package com.enterprise.brain.task.service;

import com.enterprise.brain.task.dto.request.QcStaffSaveRequest;
import com.enterprise.brain.task.dto.response.QcStaffVO;

import java.util.List;

/**
 * 质检人员维护（sys_user）
 */
public interface QcStaffService {

    /**
     * 质检人员列表，keyword 可按工号/姓名模糊过滤（为空查全部）
     */
    List<QcStaffVO> list(String keyword);

    /**
     * 新增质检人员（工号唯一；同工号曾被删除时自动恢复）
     *
     * @return 新增/恢复后的人员
     */
    QcStaffVO add(QcStaffSaveRequest request);

    /**
     * 删除质检人员（软删除 deleted=1）
     */
    void delete(Long id);

    /**
     * 维护质检人员钉钉ID（dingdingId 为空白时清空绑定）
     *
     * @return 更新后的人员
     */
    QcStaffVO updateDingdingId(Long id, String dingdingId);
}
