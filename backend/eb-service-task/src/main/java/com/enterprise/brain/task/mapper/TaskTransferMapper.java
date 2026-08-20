package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.TaskTransfer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务转办记录Mapper
 */
@Mapper
public interface TaskTransferMapper extends BaseMapper<TaskTransfer> {
}
