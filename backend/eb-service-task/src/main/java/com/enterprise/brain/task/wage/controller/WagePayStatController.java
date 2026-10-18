package com.enterprise.brain.task.wage.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wage.dto.WagePayStatReviewRequest;
import com.enterprise.brain.task.wage.dto.WageBatchMonthRequest;
import com.enterprise.brain.task.wage.entity.WagePayStat;
import com.enterprise.brain.task.wage.service.WagePayStatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工资核算-计件复核（工资统计明细）
 */
@RestController
@RequestMapping("/api/v1/wage/paystat")
@Tag(name = "工资核算-计件复核")
public class WagePayStatController {

    @Resource
    private WagePayStatService payStatService;

    @GetMapping
    public Result<List<WagePayStat>> list(@RequestParam(required = false) String month,
                                          @RequestParam(required = false) Long groupId,
                                          @RequestParam(required = false) String keyword) {
        return Result.success(payStatService.list(month, groupId, keyword));
    }

    @GetMapping("/months")
    public Result<List<String>> months() {
        return Result.success(payStatService.months());
    }

    /** 重新计算（全量删旧重算） */
    @PostMapping("/recalc")
    public Result<Map<String, Integer>> recalc(@RequestBody(required = false) Map<String, String> body) {
        String operator = body == null ? null : body.get("operator");
        int count = payStatService.recalc(operator);
        return Result.success("重新计算完成", Map.of("count", count));
    }

    /** 单条复核 */
    @PutMapping("/{id}")
    public Result<WagePayStat> review(@PathVariable Long id,
                                      @Valid @RequestBody WagePayStatReviewRequest request) {
        return Result.success("复核完成", payStatService.review(id, request));
    }

    /** 批量录入工资月份 */
    @PutMapping("/batch-month")
    public Result<Map<String, Integer>> batchMonth(@Valid @RequestBody WageBatchMonthRequest request) {
        int n = payStatService.batchUpdateMonth(request);
        return Result.success("已更新 " + n + " 条", Map.of("count", n));
    }
}
