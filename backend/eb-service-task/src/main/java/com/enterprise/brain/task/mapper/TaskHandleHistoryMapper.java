package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.TaskHandleHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务处理历史Mapper
 */
@Mapper
public interface TaskHandleHistoryMapper extends BaseMapper<TaskHandleHistory> {
}
