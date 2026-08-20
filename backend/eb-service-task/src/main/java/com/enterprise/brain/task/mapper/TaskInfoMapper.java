package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.brain.task.dto.request.TaskQueryDTO;
import com.enterprise.brain.task.entity.TaskInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 任务信息Mapper
 */
@Mapper
public interface TaskInfoMapper extends BaseMapper<TaskInfo> {

    /**
     * 分页查询任务列表
     */
    IPage<TaskInfo> selectTaskPage(Page<TaskInfo> page, @Param("query") TaskQueryDTO query);

    /**
     * 根据用户ID统计待处理数量
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND task_status = 0 AND deleted = 0")
    Long countPendingByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计处理中数量
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND task_status = 1 AND deleted = 0")
    Long countProcessingByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计已完成数量
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND task_status = 2 AND deleted = 0")
    Long countCompletedByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计超时任务数
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND task_status IN (0, 1) AND deadline_time IS NOT NULL AND deadline_time < NOW() AND deleted = 0")
    Long countOverdueByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计紧急任务数
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND priority = 1 AND task_status IN (0, 1) AND deleted = 0")
    Long countUrgentByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计未读任务数
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND is_read = 0 AND task_status IN (0, 1, 2, 3, 6) AND deleted = 0")
    Long countUnreadByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计今日新增
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND DATE(receive_time) = CURDATE() AND deleted = 0")
    Long countTodayNewByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计今日完成
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND DATE(complete_time) = CURDATE() AND task_status = 2 AND deleted = 0")
    Long countTodayCompletedByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计平均处理时长
     */
    @Select("SELECT AVG(handle_duration) FROM task_info WHERE assignee_id = #{assigneeId} AND handle_duration IS NOT NULL AND deleted = 0")
    Double avgHandleDurationByAssignee(@Param("assigneeId") Long assigneeId);

    /**
     * 统计按期完成率
     */
    @Select("SELECT COUNT(*) FROM task_info WHERE assignee_id = #{assigneeId} AND task_status = 2 AND complete_time IS NOT NULL AND deadline_time IS NOT NULL AND complete_time <= deadline_time AND deleted = 0")
    Long countOnTimeCompletedByAssignee(@Param("assigneeId") Long assigneeId);
}
