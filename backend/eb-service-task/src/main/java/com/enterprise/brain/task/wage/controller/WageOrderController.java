package com.enterprise.brain.task.wage.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wage.dto.PageVO;
import com.enterprise.brain.task.wage.dto.WageOrderDoneRequest;
import com.enterprise.brain.task.wage.dto.WageOrderStartRequest;
import com.enterprise.brain.task.wage.dto.WageOrderVO;
import com.enterprise.brain.task.wage.entity.WageMaterial;
import com.enterprise.brain.task.wage.service.WageOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工资核算-生产任务单 + 用料清单
 */
@RestController
@RequestMapping("/api/v1/wage/orders")
@Tag(name = "工资核算-生产任务单")
public class WageOrderController {

    @Resource
    private WageOrderService orderService;

    /** 任务单分页（筛选后内存分页） */
    @GetMapping("/page")
    public Result<PageVO<WageOrderVO>> page(@RequestParam(required = false) String workshop,
                                            @RequestParam(required = false) Long groupId,
                                            @RequestParam(required = false) String settleStatus,
                                            @RequestParam(required = false) String bizStatus,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(orderService.page(workshop, groupId, settleStatus, bizStatus,
                keyword, pageNum, pageSize));
    }

    /** 用料清单：taskNo 非空=该任务单下方联动；空=用料清单查询页全部 */
    @GetMapping("/materials")
    public Result<List<WageMaterial>> materials(@RequestParam(required = false) String taskNo,
                                                @RequestParam(required = false) String bizStatus,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(orderService.materials(taskNo, bizStatus, keyword));
    }

    @GetMapping("/task-nos")
    public Result<List<String>> taskNos() {
        return Result.success(orderService.taskNoOptions());
    }

    @GetMapping("/workshops")
    public Result<List<String>> workshops() {
        return Result.success(orderService.workshopOptions());
    }

    /** 从 MES 同步近一年生产订单（仅增量写入新单号） */
    @PostMapping("/sync")
    public Result<Map<String, Integer>> sync() {
        int added = orderService.syncFromMes();
        return Result.success("同步完成", Map.of("added", added));
    }

    /** 开工提交：必选组别 + 金蝶下发用料清单 */
    @PostMapping("/{id}/start")
    public Result<Boolean> start(@PathVariable Long id, @Valid @RequestBody WageOrderStartRequest request) {
        orderService.start(id, request);
        return Result.success("开工提交成功", Boolean.TRUE);
    }

    /** 确认完工 */
    @PostMapping("/{id}/done")
    public Result<Boolean> done(@PathVariable Long id, @RequestBody WageOrderDoneRequest request) {
        orderService.done(id, request);
        return Result.success("已确认完工", Boolean.TRUE);
    }

    /** 修改加工组别 */
    @PutMapping("/{id}/group")
    public Result<Boolean> changeGroup(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        orderService.changeGroup(id, body.get("groupId"));
        return Result.success("修改成功", Boolean.TRUE);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        orderService.remove(id);
        return Result.success("删除成功", Boolean.TRUE);
    }
}
