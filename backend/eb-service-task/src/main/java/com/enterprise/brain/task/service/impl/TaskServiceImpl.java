package com.enterprise.brain.task.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.common.page.PageResult;
import com.enterprise.brain.common.result.ResultCode;
import com.enterprise.brain.common.util.BusinessNoGenerator;
import com.enterprise.brain.common.util.DistributedLockUtil;
import com.enterprise.brain.task.dto.request.TaskCreateDTO;
import com.enterprise.brain.task.dto.request.TaskHandleDTO;
import com.enterprise.brain.task.dto.request.TaskQueryDTO;
import com.enterprise.brain.task.dto.response.TaskDetailDTO;
import com.enterprise.brain.task.dto.response.TaskHistoryDTO;
import com.enterprise.brain.task.dto.response.TaskListItemDTO;
import com.enterprise.brain.task.dto.response.TaskStatisticsDTO;
import com.enterprise.brain.task.entity.TaskHandleHistory;
import com.enterprise.brain.task.entity.TaskInfo;
import com.enterprise.brain.task.entity.TaskTransfer;
import com.enterprise.brain.task.enums.*;
import com.enterprise.brain.task.mapper.TaskHandleHistoryMapper;
import com.enterprise.brain.task.mapper.TaskInfoMapper;
import com.enterprise.brain.task.mapper.TaskTransferMapper;
import com.enterprise.brain.task.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一任务中心服务实现
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskInfoMapper taskInfoMapper;

    @Resource
    private TaskHandleHistoryMapper historyMapper;

    @Resource
    private TaskTransferMapper transferMapper;

    @Resource
    private org.springframework.beans.factory.ObjectProvider<DistributedLockUtil> distributedLockUtilProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(TaskCreateDTO createDTO) {
        TaskInfo task = BeanUtil.copyProperties(createDTO, TaskInfo.class);
        task.setTaskNo(BusinessNoGenerator.generateTaskNo());
        task.setTaskStatus(TaskStatusEnum.PENDING.getCode());
        task.setIsRead(0);
        task.setIsUrged(0);
        task.setUrgeCount(0);
        task.setReceiveTime(LocalDateTime.now());

        taskInfoMapper.insert(task);

        saveHistory(task.getId(), "START", "任务创建",
                createDTO.getInitiatorId() != null ? createDTO.getInitiatorId() : createDTO.getAssigneeId(),
                createDTO.getInitiatorName() != null ? createDTO.getInitiatorName() : createDTO.getAssigneeName(),
                createDTO.getAssigneeDeptId(), createDTO.getAssigneeDeptName(),
                TaskActionTypeEnum.CREATE.getCode(), null, "系统创建任务");

        log.info("创建任务成功, taskId={}, taskNo={}", task.getId(), task.getTaskNo());
        return task.getId();
    }

    @Override
    public PageResult<TaskListItemDTO> queryTaskPage(TaskQueryDTO query) {
        if (query.getPageNum() == null || query.getPageNum() < 1) {
            query.setPageNum(1L);
        }
        if (query.getPageSize() == null || query.getPageSize() < 1) {
            query.setPageSize(10L);
        }

        Page<TaskInfo> pageParam = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<TaskInfo> pageResult = taskInfoMapper.selectTaskPage(pageParam, query);

        List<TaskListItemDTO> list = pageResult.getRecords().stream()
                .map(this::convertToListItem)
                .collect(Collectors.toList());

        return PageResult.of(list, pageResult.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskDetailDTO getTaskDetail(Long taskId, Long userId) {
        TaskInfo task = taskInfoMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            throw new BusinessException(ResultCode.TASK_NOT_EXIST);
        }

        if (userId != null && task.getAssigneeId().equals(userId) && (task.getIsRead() == null || task.getIsRead() == 0)) {
            TaskInfo update = new TaskInfo();
            update.setId(task.getId());
            update.setIsRead(1);
            taskInfoMapper.updateById(update);
            task.setIsRead(1);
        }

        TaskDetailDTO detail = convertToDetail(task);

        LambdaQueryWrapper<TaskHandleHistory> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(TaskHandleHistory::getTaskId, taskId)
                .orderByAsc(TaskHandleHistory::getCreateTime);
        List<TaskHandleHistory> historyList = historyMapper.selectList(historyWrapper);

        detail.setHistoryList(historyList.stream()
                .map(this::convertToHistory)
                .collect(Collectors.toList()));

        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean handleTask(TaskHandleDTO handleDTO) {
        String lockKey = "task:handle:" + handleDTO.getTaskId();
        java.util.function.Supplier<Boolean> action = () -> {
            TaskInfo task = taskInfoMapper.selectById(handleDTO.getTaskId());
            if (task == null || task.getDeleted() == 1) {
                throw new BusinessException(ResultCode.TASK_NOT_EXIST);
            }

            String actionType = handleDTO.getActionType();
            switch (TaskActionTypeEnum.valueOf(actionType)) {
                case CLAIM:
                    return handleClaim(task, handleDTO);
                case APPROVE:
                    return handleApprove(task, handleDTO);
                case REJECT:
                    return handleReject(task, handleDTO);
                case TRANSFER:
                    return handleTransfer(task, handleDTO);
                case URGE:
                    return handleUrge(task, handleDTO);
                case COMMENT:
                    return handleComment(task, handleDTO);
                case READ:
                    return handleRead(task, handleDTO);
                default:
                    throw new BusinessException("不支持的操作类型: " + actionType);
            }
        };
        DistributedLockUtil lockUtil = distributedLockUtilProvider.getIfAvailable();
        if (lockUtil != null) {
            return lockUtil.tryLock(lockKey, 3, 60, action);
        }
        // 本地无 Redis 时跳过分布式锁，直接执行
        return action.get();
    }

    private Boolean handleClaim(TaskInfo task, TaskHandleDTO dto) {
        validateTaskStatus(task, TaskStatusEnum.PENDING);

        TaskInfo update = new TaskInfo();
        update.setId(task.getId());
        update.setTaskStatus(TaskStatusEnum.PROCESSING.getCode());
        update.setStartProcessTime(LocalDateTime.now());
        update.setIsRead(1);
        taskInfoMapper.updateById(update);

        saveHistory(task.getId(), "EXEC", "执行节点",
                dto.getHandlerId(), dto.getHandlerName(),
                dto.getHandlerDeptId(), dto.getHandlerDeptName(),
                TaskActionTypeEnum.CLAIM.getCode(), null, "领取任务");
        return true;
    }

    private Boolean handleApprove(TaskInfo task, TaskHandleDTO dto) {
        validateTaskStatus(task, TaskStatusEnum.PENDING, TaskStatusEnum.PROCESSING);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = task.getStartProcessTime() != null ? task.getStartProcessTime() : task.getReceiveTime();
        long durationSeconds = Duration.between(startTime, now).getSeconds();

        TaskInfo update = new TaskInfo();
        update.setId(task.getId());
        update.setTaskStatus(TaskStatusEnum.COMPLETED.getCode());
        update.setCompleteTime(now);
        update.setStartProcessTime(task.getStartProcessTime() != null ? task.getStartProcessTime() : now);
        update.setHandleDuration(durationSeconds);
        update.setActionResult("AGREE");
        update.setActionComment(dto.getActionComment());
        update.setIsRead(1);
        taskInfoMapper.updateById(update);

        saveHistory(task.getId(), "APPROVE", "审批节点",
                dto.getHandlerId(), dto.getHandlerName(),
                dto.getHandlerDeptId(), dto.getHandlerDeptName(),
                TaskActionTypeEnum.APPROVE.getCode(), "AGREE",
                dto.getActionComment(), durationSeconds);
        return true;
    }

    private Boolean handleReject(TaskInfo task, TaskHandleDTO dto) {
        validateTaskStatus(task, TaskStatusEnum.PENDING, TaskStatusEnum.PROCESSING);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = task.getStartProcessTime() != null ? task.getStartProcessTime() : task.getReceiveTime();
        long durationSeconds = Duration.between(startTime, now).getSeconds();

        TaskInfo update = new TaskInfo();
        update.setId(task.getId());
        update.setTaskStatus(TaskStatusEnum.REJECTED.getCode());
        update.setCompleteTime(now);
        update.setStartProcessTime(task.getStartProcessTime() != null ? task.getStartProcessTime() : now);
        update.setHandleDuration(durationSeconds);
        update.setActionResult("REJECT");
        update.setActionComment(dto.getActionComment());
        update.setIsRead(1);
        taskInfoMapper.updateById(update);

        saveHistory(task.getId(), "REJECT", "审批节点",
                dto.getHandlerId(), dto.getHandlerName(),
                dto.getHandlerDeptId(), dto.getHandlerDeptName(),
                TaskActionTypeEnum.REJECT.getCode(), "REJECT",
                dto.getActionComment(), durationSeconds);
        return true;
    }

    private Boolean handleTransfer(TaskInfo task, TaskHandleDTO dto) {
        validateTaskStatus(task, TaskStatusEnum.PENDING, TaskStatusEnum.PROCESSING);

        if (dto.getTransferToUserId() == null) {
            throw new BusinessException(ResultCode.VALIDATE_ERROR, "转办目标人不能为空");
        }

        TaskInfo update = new TaskInfo();
        update.setId(task.getId());
        update.setAssigneeId(dto.getTransferToUserId());
        update.setAssigneeName(dto.getTransferToUserName());
        update.setTaskStatus(TaskStatusEnum.PENDING.getCode());
        update.setStartProcessTime(null);
        update.setIsRead(0);
        update.setReceiveTime(LocalDateTime.now());
        taskInfoMapper.updateById(update);

        TaskTransfer transfer = new TaskTransfer();
        transfer.setTaskId(task.getId());
        transfer.setTransferType(1);
        transfer.setFromUserId(dto.getHandlerId());
        transfer.setFromUserName(dto.getHandlerName());
        transfer.setToUserId(dto.getTransferToUserId());
        transfer.setToUserName(dto.getTransferToUserName());
        transfer.setReason(dto.getTransferReason());
        transfer.setTransferTime(LocalDateTime.now());
        transfer.setIsReturn(0);
        transferMapper.insert(transfer);

        saveHistory(task.getId(), "TRANSFER", "转办节点",
                dto.getHandlerId(), dto.getHandlerName(),
                dto.getHandlerDeptId(), dto.getHandlerDeptName(),
                TaskActionTypeEnum.TRANSFER.getCode(), null,
                "转办给：" + dto.getTransferToUserName() + "，原因：" + dto.getTransferReason());
        return true;
    }

    private Boolean handleUrge(TaskInfo task, TaskHandleDTO dto) {
        if (task.getTaskStatus().equals(TaskStatusEnum.COMPLETED.getCode())
                || task.getTaskStatus().equals(TaskStatusEnum.REJECTED.getCode())
                || task.getTaskStatus().equals(TaskStatusEnum.REVOKED.getCode())) {
            throw new BusinessException(ResultCode.TASK_ALREADY_HANDLED);
        }

        TaskInfo update = new TaskInfo();
        update.setId(task.getId());
        update.setIsUrged(1);
        update.setUrgeCount((task.getUrgeCount() == null ? 0 : task.getUrgeCount()) + 1);
        update.setSortWeight((task.getSortWeight() == null ? 0 : task.getSortWeight()) + 10);
        taskInfoMapper.updateById(update);

        saveHistory(task.getId(), "URGE", "催办",
                dto.getHandlerId(), dto.getHandlerName(),
                dto.getHandlerDeptId(), dto.getHandlerDeptName(),
                TaskActionTypeEnum.URGE.getCode(), null,
                "催办，备注：" + dto.getActionComment());
        return true;
    }

    private Boolean handleComment(TaskInfo task, TaskHandleDTO dto) {
        saveHistory(task.getId(), "COMMENT", "评论",
                dto.getHandlerId(), dto.getHandlerName(),
                dto.getHandlerDeptId(), dto.getHandlerDeptName(),
                TaskActionTypeEnum.COMMENT.getCode(), null,
                dto.getActionComment());
        return true;
    }

    private Boolean handleRead(TaskInfo task, TaskHandleDTO dto) {
        if (task.getIsRead() != null && task.getIsRead() == 1) {
            return true;
        }
        TaskInfo update = new TaskInfo();
        update.setId(task.getId());
        update.setIsRead(1);
        taskInfoMapper.updateById(update);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer markReadBatch(List<Long> taskIds, Long userId) {
        if (taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<TaskInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TaskInfo::getId, taskIds)
                .eq(TaskInfo::getAssigneeId, userId)
                .eq(TaskInfo::getIsRead, 0)
                .eq(TaskInfo::getDeleted, 0);
        List<TaskInfo> tasks = taskInfoMapper.selectList(wrapper);
        int count = 0;
        for (TaskInfo task : tasks) {
            TaskInfo update = new TaskInfo();
            update.setId(task.getId());
            update.setIsRead(1);
            count += taskInfoMapper.updateById(update);
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer markAllRead(Long assigneeId) {
        LambdaQueryWrapper<TaskInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskInfo::getAssigneeId, assigneeId)
                .eq(TaskInfo::getIsRead, 0)
                .eq(TaskInfo::getDeleted, 0);
        List<TaskInfo> tasks = taskInfoMapper.selectList(wrapper);
        int count = 0;
        for (TaskInfo task : tasks) {
            TaskInfo update = new TaskInfo();
            update.setId(task.getId());
            update.setIsRead(1);
            count += taskInfoMapper.updateById(update);
        }
        return count;
    }

    @Override
    public TaskStatisticsDTO getStatistics(Long userId) {
        TaskStatisticsDTO stats = new TaskStatisticsDTO();
        stats.setUserId(userId);
        stats.setPendingCount(nullToZero(taskInfoMapper.countPendingByAssignee(userId)));
        stats.setProcessingCount(nullToZero(taskInfoMapper.countProcessingByAssignee(userId)));
        stats.setCompletedCount(nullToZero(taskInfoMapper.countCompletedByAssignee(userId)));
        stats.setOverdueCount(nullToZero(taskInfoMapper.countOverdueByAssignee(userId)));
        stats.setUrgentCount(nullToZero(taskInfoMapper.countUrgentByAssignee(userId)));
        stats.setUnreadCount(nullToZero(taskInfoMapper.countUnreadByAssignee(userId)));
        stats.setTodayNewCount(nullToZero(taskInfoMapper.countTodayNewByAssignee(userId)));
        stats.setTodayCompletedCount(nullToZero(taskInfoMapper.countTodayCompletedByAssignee(userId)));

        Double avgDuration = taskInfoMapper.avgHandleDurationByAssignee(userId);
        stats.setAvgHandleDuration(avgDuration != null ? Math.round(avgDuration * 100.0) / 100.0 : 0.0);

        Long onTimeCount = nullToZero(taskInfoMapper.countOnTimeCompletedByAssignee(userId));
        long completedCount = stats.getCompletedCount();
        if (completedCount > 0) {
            stats.setOnTimeRate(Math.round(onTimeCount * 10000.0 / completedCount) / 100.0);
        } else {
            stats.setOnTimeRate(100.0);
        }

        return stats;
    }

    private void validateTaskStatus(TaskInfo task, TaskStatusEnum... expectedStatuses) {
        if (task.getTaskStatus() == null) {
            throw new BusinessException(ResultCode.TASK_STATUS_ERROR);
        }
        for (TaskStatusEnum expected : expectedStatuses) {
            if (expected.getCode().equals(task.getTaskStatus())) {
                return;
            }
        }
        throw new BusinessException(ResultCode.TASK_STATUS_ERROR,
                "当前任务状态为【" + TaskStatusEnum.getDesc(task.getTaskStatus()) + "】，不允许此操作");
    }

    private void saveHistory(Long taskId, String nodeCode, String nodeName,
                             Long handlerId, String handlerName,
                             Long handlerDeptId, String handlerDeptName,
                             String actionType, String actionResult, String actionComment) {
        saveHistory(taskId, nodeCode, nodeName, handlerId, handlerName, handlerDeptId, handlerDeptName,
                actionType, actionResult, actionComment, null);
    }

    private void saveHistory(Long taskId, String nodeCode, String nodeName,
                             Long handlerId, String handlerName,
                             Long handlerDeptId, String handlerDeptName,
                             String actionType, String actionResult, String actionComment,
                             Long handleDuration) {
        TaskHandleHistory history = new TaskHandleHistory();
        history.setTaskId(taskId);
        history.setNodeCode(nodeCode);
        history.setNodeName(nodeName);
        history.setHandlerId(handlerId);
        history.setHandlerName(handlerName);
        history.setHandlerDeptId(handlerDeptId);
        history.setHandlerDeptName(handlerDeptName);
        history.setActionType(actionType);
        history.setActionResult(actionResult);
        history.setActionComment(actionComment);
        history.setHandleDuration(handleDuration);
        history.setCreateTime(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private TaskListItemDTO convertToListItem(TaskInfo task) {
        TaskListItemDTO dto = BeanUtil.copyProperties(task, TaskListItemDTO.class);
        dto.setExternalSystemName(ExternalSystemEnum.getDesc(task.getExternalSystem()));
        dto.setTaskTypeDesc(TaskTypeEnum.getDesc(task.getTaskType()));
        dto.setTaskCategoryDesc(TaskCategoryEnum.getDesc(task.getTaskCategory()));
        dto.setPriorityDesc(TaskPriorityEnum.getDesc(task.getPriority()));
        dto.setTaskStatusDesc(TaskStatusEnum.getDesc(task.getTaskStatus()));

        LocalDateTime now = LocalDateTime.now();
        boolean isOverdue = task.getDeadlineTime() != null
                && task.getDeadlineTime().isBefore(now)
                && (TaskStatusEnum.PENDING.getCode().equals(task.getTaskStatus())
                || TaskStatusEnum.PROCESSING.getCode().equals(task.getTaskStatus()));
        boolean isNearDeadline = task.getDeadlineTime() != null
                && !isOverdue
                && Duration.between(now, task.getDeadlineTime()).toHours() <= 24;
        dto.setIsOverdue(isOverdue);
        dto.setIsNearDeadline(isNearDeadline);

        return dto;
    }

    private TaskDetailDTO convertToDetail(TaskInfo task) {
        TaskDetailDTO dto = BeanUtil.copyProperties(task, TaskDetailDTO.class);
        dto.setExternalSystemName(ExternalSystemEnum.getDesc(task.getExternalSystem()));
        dto.setTaskTypeDesc(TaskTypeEnum.getDesc(task.getTaskType()));
        dto.setTaskCategoryDesc(TaskCategoryEnum.getDesc(task.getTaskCategory()));
        dto.setPriorityDesc(TaskPriorityEnum.getDesc(task.getPriority()));
        dto.setTaskStatusDesc(TaskStatusEnum.getDesc(task.getTaskStatus()));
        dto.setActionResultDesc(actionResultDesc(task.getActionResult()));
        dto.setHandleDurationDisplay(formatDuration(task.getHandleDuration()));

        LocalDateTime now = LocalDateTime.now();
        boolean isOverdue = task.getDeadlineTime() != null
                && task.getDeadlineTime().isBefore(now)
                && (TaskStatusEnum.PENDING.getCode().equals(task.getTaskStatus())
                || TaskStatusEnum.PROCESSING.getCode().equals(task.getTaskStatus()));
        boolean isNearDeadline = task.getDeadlineTime() != null
                && !isOverdue
                && Duration.between(now, task.getDeadlineTime()).toHours() <= 24;
        dto.setIsOverdue(isOverdue);
        dto.setIsNearDeadline(isNearDeadline);

        return dto;
    }

    private TaskHistoryDTO convertToHistory(TaskHandleHistory history) {
        TaskHistoryDTO dto = BeanUtil.copyProperties(history, TaskHistoryDTO.class);
        dto.setActionTypeDesc(TaskActionTypeEnum.getDesc(history.getActionType()));
        dto.setActionResultDesc(actionResultDesc(history.getActionResult()));
        dto.setHandleDurationDisplay(formatDuration(history.getHandleDuration()));
        return dto;
    }

    private String actionResultDesc(String result) {
        if (StrUtil.isBlank(result)) {
            return "";
        }
        switch (result) {
            case "AGREE":
                return "同意";
            case "REJECT":
                return "驳回";
            case "TRANSFER":
                return "转办";
            default:
                return result;
        }
    }

    private String formatDuration(Long seconds) {
        if (seconds == null || seconds <= 0) {
            return "";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "小时" + minutes + "分" + secs + "秒";
        } else if (minutes > 0) {
            return minutes + "分" + secs + "秒";
        } else {
            return secs + "秒";
        }
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
