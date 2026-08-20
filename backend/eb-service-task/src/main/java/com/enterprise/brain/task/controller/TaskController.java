package com.enterprise.brain.task.controller;

import com.enterprise.brain.common.page.PageResult;
import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.dto.request.TaskCreateDTO;
import com.enterprise.brain.task.dto.request.TaskHandleDTO;
import com.enterprise.brain.task.dto.request.TaskQueryDTO;
import com.enterprise.brain.task.dto.response.TaskDetailDTO;
import com.enterprise.brain.task.dto.response.TaskListItemDTO;
import com.enterprise.brain.task.dto.response.TaskStatisticsDTO;
import com.enterprise.brain.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 统一任务中心接口
 */
@RestController
@RequestMapping("/api/v1/task")
@Tag(name = "统一任务中心", description = "待办/已办/任务管理相关接口")
public class TaskController {

    @Resource
    private TaskService taskService;

    @PostMapping
    @Operation(summary = "创建任务", description = "创建一个新的任务（系统自建或外部系统同步）")
    public Result<Long> createTask(@Valid @RequestBody TaskCreateDTO createDTO) {
        return Result.success(taskService.createTask(createDTO));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询任务列表", description = "按条件分页查询任务列表，支持按状态、来源系统、优先级等筛选")
    public Result<PageResult<TaskListItemDTO>> queryTaskPage(@Valid TaskQueryDTO query) {
        return Result.success(taskService.queryTaskPage(query));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "获取任务详情", description = "根据任务ID获取详细信息，包含处理历史")
    public Result<TaskDetailDTO> getTaskDetail(
            @Parameter(description = "任务ID", required = true) @PathVariable Long taskId,
            @Parameter(description = "当前用户ID（用于自动标记已读）") @RequestParam(required = false) Long userId) {
        return Result.success(taskService.getTaskDetail(taskId, userId));
    }

    @PostMapping("/handle")
    @Operation(summary = "处理任务", description = "处理任务：领取、同意、驳回、转办、催办、评论等操作")
    public Result<Boolean> handleTask(@Valid @RequestBody TaskHandleDTO handleDTO) {
        return Result.success(taskService.handleTask(handleDTO));
    }

    @PostMapping("/read/batch")
    @Operation(summary = "批量标记已读", description = "将指定任务列表标记为已读")
    public Result<Integer> markReadBatch(
            @Parameter(description = "任务ID列表", required = true) @RequestBody List<Long> taskIds,
            @Parameter(description = "用户ID", required = true) @RequestParam Long userId) {
        return Result.success(taskService.markReadBatch(taskIds, userId));
    }

    @PostMapping("/read/all")
    @Operation(summary = "标记全部已读", description = "将当前用户的所有任务标记为已读")
    public Result<Integer> markAllRead(
            @Parameter(description = "处理人ID", required = true) @RequestParam Long assigneeId) {
        return Result.success(taskService.markAllRead(assigneeId));
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取任务统计", description = "获取当前用户的任务统计数据：待办数、完成数、超时数、按期完成率等")
    public Result<TaskStatisticsDTO> getStatistics(
            @Parameter(description = "用户ID", required = true) @RequestParam Long userId) {
        return Result.success(taskService.getStatistics(userId));
    }
}
