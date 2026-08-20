package com.enterprise.brain.task.service;

import com.enterprise.brain.common.page.PageResult;
import com.enterprise.brain.task.dto.request.TaskCreateDTO;
import com.enterprise.brain.task.dto.request.TaskHandleDTO;
import com.enterprise.brain.task.dto.request.TaskQueryDTO;
import com.enterprise.brain.task.dto.response.TaskDetailDTO;
import com.enterprise.brain.task.dto.response.TaskListItemDTO;
import com.enterprise.brain.task.dto.response.TaskStatisticsDTO;

/**
 * 统一任务中心服务接口
 */
public interface TaskService {

    /**
     * 创建任务
     *
     * @param createDTO 创建请求
     * @return 任务ID
     */
    Long createTask(TaskCreateDTO createDTO);

    /**
     * 分页查询任务列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<TaskListItemDTO> queryTaskPage(TaskQueryDTO query);

    /**
     * 获取任务详情
     *
     * @param taskId   任务ID
     * @param userId   当前用户ID（用于标记已读，可为null）
     * @return 任务详情
     */
    TaskDetailDTO getTaskDetail(Long taskId, Long userId);

    /**
     * 处理任务（审批、驳回、领取、转办、催办、评论等）
     *
     * @param handleDTO 处理请求
     * @return 是否成功
     */
    Boolean handleTask(TaskHandleDTO handleDTO);

    /**
     * 批量标记已读
     *
     * @param taskIds 任务ID列表
     * @param userId  用户ID
     * @return 已读数量
     */
    Integer markReadBatch(java.util.List<Long> taskIds, Long userId);

    /**
     * 标记所有任务已读
     *
     * @param assigneeId 处理人ID
     * @return 已读数量
     */
    Integer markAllRead(Long assigneeId);

    /**
     * 获取用户任务统计数据
     *
     * @param userId 用户ID
     * @return 统计数据
     */
    TaskStatisticsDTO getStatistics(Long userId);
}
