package com.enterprise.brain.task.service;

import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.common.page.PageResult;
import com.enterprise.brain.task.TestConfig;
import com.enterprise.brain.task.dto.request.TaskCreateDTO;
import com.enterprise.brain.task.dto.request.TaskHandleDTO;
import com.enterprise.brain.task.dto.request.TaskQueryDTO;
import com.enterprise.brain.task.dto.response.TaskDetailDTO;
import com.enterprise.brain.task.dto.response.TaskListItemDTO;
import com.enterprise.brain.task.dto.response.TaskStatisticsDTO;
import com.enterprise.brain.task.entity.TaskInfo;
import com.enterprise.brain.task.enums.TaskStatusEnum;
import com.enterprise.brain.task.mapper.TaskHandleHistoryMapper;
import com.enterprise.brain.task.mapper.TaskInfoMapper;
import com.enterprise.brain.task.mapper.TaskTransferMapper;
import com.enterprise.brain.task.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * TaskService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {TestConfig.class})
class TaskServiceTest {

    @Mock
    private TaskInfoMapper taskInfoMapper;

    @Mock
    private TaskHandleHistoryMapper historyMapper;

    @Mock
    private TaskTransferMapper transferMapper;

    @Mock
    private com.enterprise.brain.common.util.DistributedLockUtil distributedLockUtil;

    private TaskServiceImpl taskService;

    private TaskInfo createMockTask(Long id, Integer status) {
        TaskInfo task = new TaskInfo();
        task.setId(id);
        task.setTaskNo("TK2026081500" + id);
        task.setExternalSystem("EB");
        task.setTaskType("APPROVAL");
        task.setTaskCategory("LEAVE");
        task.setPriority(2);
        task.setTitle("测试任务-" + id);
        task.setContent("测试任务内容");
        task.setBizKey("BIZ-" + id);
        task.setInitiatorId(2L);
        task.setInitiatorName("发起人");
        task.setAssigneeId(1L);
        task.setAssigneeName("处理人");
        task.setAssigneeDeptId(1L);
        task.setAssigneeDeptName("测试部门");
        task.setTaskStatus(status);
        task.setReceiveTime(LocalDateTime.now().minusHours(1));
        task.setDeadlineTime(LocalDateTime.now().plusDays(1));
        task.setIsRead(0);
        task.setIsUrged(0);
        task.setUrgeCount(0);
        task.setSortWeight(0);
        task.setDeleted(0);
        return task;
    }

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl();

        // 使用反射或手动setter注入
        try {
            java.lang.reflect.Field f1 = TaskServiceImpl.class.getDeclaredField("taskInfoMapper");
            f1.setAccessible(true);
            f1.set(taskService, taskInfoMapper);

            java.lang.reflect.Field f2 = TaskServiceImpl.class.getDeclaredField("historyMapper");
            f2.setAccessible(true);
            f2.set(taskService, historyMapper);

            java.lang.reflect.Field f3 = TaskServiceImpl.class.getDeclaredField("transferMapper");
            f3.setAccessible(true);
            f3.set(taskService, transferMapper);

            java.lang.reflect.Field f4 = TaskServiceImpl.class.getDeclaredField("distributedLockUtil");
            f4.setAccessible(true);
            f4.set(taskService, distributedLockUtil);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // mock分布式锁 - 直接执行
        when(distributedLockUtil.tryLock(any(String.class), anyLong(), anyLong(), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(3)).get());

        // Runnable版本tryLock返回void，void方法不能用when()打桩，必须用doAnswer().when()风格
        doAnswer(invocation -> {
                    ((Runnable) invocation.getArgument(3)).run();
                    return null;
                }).when(distributedLockUtil)
                .tryLock(any(String.class), anyLong(), anyLong(), any(Runnable.class));
    }

    @Nested
    @DisplayName("创建任务测试")
    class CreateTaskTest {

        @Test
        @DisplayName("创建任务成功")
        void createTask_success() {
            TaskCreateDTO dto = new TaskCreateDTO();
            dto.setExternalSystem("EB");
            dto.setTaskType("APPROVAL");
            dto.setTitle("测试创建任务");
            dto.setAssigneeId(1L);
            dto.setAssigneeName("处理人");
            dto.setInitiatorId(2L);
            dto.setInitiatorName("发起人");
            dto.setAssigneeDeptId(1L);
            dto.setAssigneeDeptName("测试部门");
            dto.setContent("任务内容");
            dto.setPriority(2);

            when(taskInfoMapper.insert(any(TaskInfo.class))).thenAnswer(invocation -> {
                TaskInfo task = invocation.getArgument(0);
                task.setId(9999L);
                return 1;
            });

            Long taskId = taskService.createTask(dto);

            assertNotNull(taskId);
            assertEquals(9999L, taskId);
            verify(taskInfoMapper, times(1)).insert(any(TaskInfo.class));
            verify(historyMapper, times(1)).insert(any());
        }
    }

    @Nested
    @DisplayName("任务查询测试")
    class QueryTaskTest {

        @Test
        @DisplayName("分页查询任务列表")
        void queryTaskPage_success() {
            TaskQueryDTO query = new TaskQueryDTO();
            query.setPageNum(1L);
            query.setPageSize(10L);
            query.setAssigneeId(1L);
            query.setTaskStatus(TaskStatusEnum.PENDING.getCode());

            List<TaskInfo> records = Arrays.asList(
                    createMockTask(1001L, 0),
                    createMockTask(1002L, 0)
            );

            com.baomidou.mybatisplus.core.metadata.IPage<TaskInfo> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            page.setRecords(records);
            page.setTotal(25L);

            when(taskInfoMapper.selectTaskPage(any(), any())).thenReturn(page);

            PageResult<TaskListItemDTO> result = taskService.queryTaskPage(query);

            assertNotNull(result);
            assertEquals(25L, result.getTotal());
            assertEquals(1L, result.getPageNum());
            assertEquals(10L, result.getPageSize());
            assertEquals(2, result.getList().size());
            assertEquals(3L, result.getTotalPages());
        }

        @Test
        @DisplayName("分页查询空结果")
        void queryTaskPage_empty() {
            TaskQueryDTO query = new TaskQueryDTO();
            query.setPageNum(1L);
            query.setPageSize(10L);

            com.baomidou.mybatisplus.core.metadata.IPage<TaskInfo> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            page.setRecords(new ArrayList<>());
            page.setTotal(0L);

            when(taskInfoMapper.selectTaskPage(any(), any())).thenReturn(page);

            PageResult<TaskListItemDTO> result = taskService.queryTaskPage(query);

            assertNotNull(result);
            assertTrue(result.getList().isEmpty());
            assertEquals(0L, result.getTotal());
        }
    }

    @Nested
    @DisplayName("任务详情测试")
    class GetDetailTest {

        @Test
        @DisplayName("获取任务详情成功")
        void getDetail_success() {
            TaskInfo task = createMockTask(1001L, 0);
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);
            when(historyMapper.selectList(any())).thenReturn(new ArrayList<>());
            when(taskInfoMapper.updateById(any(TaskInfo.class))).thenReturn(1);

            TaskDetailDTO detail = taskService.getTaskDetail(1001L, 1L);

            assertNotNull(detail);
            assertEquals(1001L, detail.getId());
            assertEquals("测试任务-1001", detail.getTitle());
            assertEquals("待处理", detail.getTaskStatusDesc());
        }

        @Test
        @DisplayName("获取不存在任务抛异常")
        void getDetail_notExist() {
            when(taskInfoMapper.selectById(9999L)).thenReturn(null);

            assertThrows(BusinessException.class, () -> taskService.getTaskDetail(9999L, 1L));
        }

        @Test
        @DisplayName("获取已删除任务抛异常")
        void getDetail_deleted() {
            TaskInfo task = createMockTask(1001L, 0);
            task.setDeleted(1);
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);

            assertThrows(BusinessException.class, () -> taskService.getTaskDetail(1001L, 1L));
        }
    }

    @Nested
    @DisplayName("任务处理测试")
    class HandleTaskTest {

        @Test
        @DisplayName("领取任务成功")
        void claimTask_success() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.PENDING.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);
            when(taskInfoMapper.updateById(any(TaskInfo.class))).thenReturn(1);
            when(historyMapper.insert(any())).thenReturn(1);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(1L);
            dto.setHandlerName("处理人");
            dto.setHandlerDeptId(1L);
            dto.setHandlerDeptName("测试部门");
            dto.setActionType("CLAIM");

            Boolean result = taskService.handleTask(dto);

            assertTrue(result);
            verify(taskInfoMapper, times(1)).updateById(argThat(t ->
                    TaskStatusEnum.PROCESSING.getCode().equals(t.getTaskStatus())
                            && t.getStartProcessTime() != null
                            && Integer.valueOf(1).equals(t.getIsRead())
            ));
        }

        @Test
        @DisplayName("同意任务成功")
        void approveTask_success() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.PENDING.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);
            when(taskInfoMapper.updateById(any(TaskInfo.class))).thenReturn(1);
            when(historyMapper.insert(any())).thenReturn(1);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(1L);
            dto.setHandlerName("处理人");
            dto.setHandlerDeptId(1L);
            dto.setHandlerDeptName("测试部门");
            dto.setActionType("APPROVE");
            dto.setActionComment("同意申请");

            Boolean result = taskService.handleTask(dto);

            assertTrue(result);
            verify(taskInfoMapper, times(1)).updateById(argThat(t ->
                    TaskStatusEnum.COMPLETED.getCode().equals(t.getTaskStatus())
                            && "AGREE".equals(t.getActionResult())
                            && "同意申请".equals(t.getActionComment())
                            && t.getCompleteTime() != null
            ));
        }

        @Test
        @DisplayName("驳回任务成功")
        void rejectTask_success() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.PROCESSING.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);
            when(taskInfoMapper.updateById(any(TaskInfo.class))).thenReturn(1);
            when(historyMapper.insert(any())).thenReturn(1);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(1L);
            dto.setHandlerName("处理人");
            dto.setHandlerDeptId(1L);
            dto.setHandlerDeptName("测试部门");
            dto.setActionType("REJECT");
            dto.setActionComment("资料不全，退回补充");

            Boolean result = taskService.handleTask(dto);

            assertTrue(result);
            verify(taskInfoMapper, times(1)).updateById(argThat(t ->
                    TaskStatusEnum.REJECTED.getCode().equals(t.getTaskStatus())
                            && "REJECT".equals(t.getActionResult())
            ));
        }

        @Test
        @DisplayName("已完成任务不能再审批")
        void approveTask_completed_shouldFail() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.COMPLETED.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(1L);
            dto.setHandlerName("处理人");
            dto.setActionType("APPROVE");

            assertThrows(BusinessException.class, () -> taskService.handleTask(dto));
        }

        @Test
        @DisplayName("转办任务成功")
        void transferTask_success() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.PENDING.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);
            when(taskInfoMapper.updateById(any(TaskInfo.class))).thenReturn(1);
            when(historyMapper.insert(any())).thenReturn(1);
            when(transferMapper.insert(any())).thenReturn(1);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(1L);
            dto.setHandlerName("原处理人");
            dto.setActionType("TRANSFER");
            dto.setTransferToUserId(3L);
            dto.setTransferToUserName("新处理人");
            dto.setTransferReason("出差，转办处理");

            Boolean result = taskService.handleTask(dto);

            assertTrue(result);
            verify(taskInfoMapper, times(1)).updateById(argThat(t ->
                    Long.valueOf(3L).equals(t.getAssigneeId())
                            && "新处理人".equals(t.getAssigneeName())
                            && TaskStatusEnum.PENDING.getCode().equals(t.getTaskStatus())
            ));
            verify(transferMapper, times(1)).insert(any());
        }

        @Test
        @DisplayName("转办任务目标人为空抛异常")
        void transferTask_noTarget_shouldFail() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.PENDING.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(1L);
            dto.setHandlerName("处理人");
            dto.setActionType("TRANSFER");

            assertThrows(BusinessException.class, () -> taskService.handleTask(dto));
        }

        @Test
        @DisplayName("催办任务成功")
        void urgeTask_success() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.PENDING.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);
            when(taskInfoMapper.updateById(any(TaskInfo.class))).thenReturn(1);
            when(historyMapper.insert(any())).thenReturn(1);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(99L);
            dto.setHandlerName("管理员");
            dto.setActionType("URGE");
            dto.setActionComment("请尽快处理");

            Boolean result = taskService.handleTask(dto);

            assertTrue(result);
            verify(taskInfoMapper, times(1)).updateById(argThat(t ->
                    Integer.valueOf(1).equals(t.getIsUrged())
                            && Integer.valueOf(1).equals(t.getUrgeCount())
            ));
        }

        @Test
        @DisplayName("对已完成任务催办失败")
        void urgeTask_completed_shouldFail() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.COMPLETED.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(99L);
            dto.setHandlerName("管理员");
            dto.setActionType("URGE");

            assertThrows(BusinessException.class, () -> taskService.handleTask(dto));
        }

        @Test
        @DisplayName("评论任务成功")
        void commentTask_success() {
            TaskInfo task = createMockTask(1001L, TaskStatusEnum.PROCESSING.getCode());
            when(taskInfoMapper.selectById(1001L)).thenReturn(task);
            when(historyMapper.insert(any())).thenReturn(1);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(1001L);
            dto.setHandlerId(2L);
            dto.setHandlerName("发起人");
            dto.setActionType("COMMENT");
            dto.setActionComment("补充说明一下，这个任务比较急~");

            Boolean result = taskService.handleTask(dto);

            assertTrue(result);
            verify(historyMapper, times(1)).insert(any());
        }

        @Test
        @DisplayName("处理不存在的任务抛异常")
        void handleTask_notExist() {
            when(taskInfoMapper.selectById(9999L)).thenReturn(null);

            TaskHandleDTO dto = new TaskHandleDTO();
            dto.setTaskId(9999L);
            dto.setHandlerId(1L);
            dto.setHandlerName("处理人");
            dto.setActionType("APPROVE");

            assertThrows(BusinessException.class, () -> taskService.handleTask(dto));
        }
    }

    @Nested
    @DisplayName("标为已读测试")
    class MarkReadTest {

        @Test
        @DisplayName("批量标记已读")
        void markReadBatch_success() {
            List<Long> ids = Arrays.asList(1001L, 1002L, 1003L);

            List<TaskInfo> tasks = Arrays.asList(
                    createMockTask(1001L, 0),
                    createMockTask(1002L, 0)
            );
            when(taskInfoMapper.selectList(any())).thenReturn(tasks);
            when(taskInfoMapper.updateById(any(TaskInfo.class))).thenReturn(1);

            Integer count = taskService.markReadBatch(ids, 1L);

            assertEquals(2, count);
            verify(taskInfoMapper, times(2)).updateById(any());
        }

        @Test
        @DisplayName("批量标记已读-空列表")
        void markReadBatch_emptyList() {
            Integer count = taskService.markReadBatch(Collections.emptyList(), 1L);

            assertEquals(0, count);
            verify(taskInfoMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("全部标记已读")
        void markAllRead_success() {
            List<TaskInfo> tasks = Arrays.asList(
                    createMockTask(1001L, 0),
                    createMockTask(1002L, 1),
                    createMockTask(1003L, 2)
            );
            tasks.forEach(t -> t.setIsRead(0));

            when(taskInfoMapper.selectList(any())).thenReturn(tasks);
            when(taskInfoMapper.updateById(any(TaskInfo.class))).thenReturn(1);

            Integer count = taskService.markAllRead(1L);

            assertEquals(3, count);
            verify(taskInfoMapper, times(3)).updateById(any());
        }
    }

    @Nested
    @DisplayName("统计测试")
    class StatisticsTest {

        @Test
        @DisplayName("获取统计数据成功")
        void getStatistics_success() {
            when(taskInfoMapper.countPendingByAssignee(1L)).thenReturn(5L);
            when(taskInfoMapper.countProcessingByAssignee(1L)).thenReturn(2L);
            when(taskInfoMapper.countCompletedByAssignee(1L)).thenReturn(25L);
            when(taskInfoMapper.countOverdueByAssignee(1L)).thenReturn(1L);
            when(taskInfoMapper.countUrgentByAssignee(1L)).thenReturn(2L);
            when(taskInfoMapper.countUnreadByAssignee(1L)).thenReturn(4L);
            when(taskInfoMapper.countTodayNewByAssignee(1L)).thenReturn(3L);
            when(taskInfoMapper.countTodayCompletedByAssignee(1L)).thenReturn(2L);
            when(taskInfoMapper.avgHandleDurationByAssignee(1L)).thenReturn(3300.0);
            when(taskInfoMapper.countOnTimeCompletedByAssignee(1L)).thenReturn(24L);

            TaskStatisticsDTO stats = taskService.getStatistics(1L);

            assertNotNull(stats);
            assertEquals(5L, stats.getPendingCount());
            assertEquals(2L, stats.getProcessingCount());
            assertEquals(25L, stats.getCompletedCount());
            assertEquals(1L, stats.getOverdueCount());
            assertEquals(2L, stats.getUrgentCount());
            assertEquals(4L, stats.getUnreadCount());
            assertEquals(3L, stats.getTodayNewCount());
            assertEquals(2L, stats.getTodayCompletedCount());
            assertEquals(33.0, stats.getAvgHandleDuration(), 0.01);
            // 24/25 = 96.0%
            assertEquals(96.0, stats.getOnTimeRate(), 0.01);
        }

        @Test
        @DisplayName("统计数据-完成数为0时按期完成率默认100%")
        void getStatistics_noCompleted() {
            when(taskInfoMapper.countPendingByAssignee(1L)).thenReturn(0L);
            when(taskInfoMapper.countProcessingByAssignee(1L)).thenReturn(0L);
            when(taskInfoMapper.countCompletedByAssignee(1L)).thenReturn(0L);
            when(taskInfoMapper.countOverdueByAssignee(1L)).thenReturn(0L);
            when(taskInfoMapper.countUrgentByAssignee(1L)).thenReturn(0L);
            when(taskInfoMapper.countUnreadByAssignee(1L)).thenReturn(0L);
            when(taskInfoMapper.countTodayNewByAssignee(1L)).thenReturn(0L);
            when(taskInfoMapper.countTodayCompletedByAssignee(1L)).thenReturn(0L);
            when(taskInfoMapper.avgHandleDurationByAssignee(1L)).thenReturn(null);
            when(taskInfoMapper.countOnTimeCompletedByAssignee(1L)).thenReturn(null);

            TaskStatisticsDTO stats = taskService.getStatistics(1L);

            assertEquals(0L, stats.getCompletedCount());
            assertEquals(0.0, stats.getAvgHandleDuration(), 0.001);
            assertEquals(100.0, stats.getOnTimeRate(), 0.001);
        }
    }
}
