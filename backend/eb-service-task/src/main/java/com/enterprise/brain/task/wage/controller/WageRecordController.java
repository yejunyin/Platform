package com.enterprise.brain.task.wage.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wage.dto.WageAuditRequest;
import com.enterprise.brain.task.wage.dto.WageSummaryVO;
import com.enterprise.brain.task.wage.entity.WageRecord;
import com.enterprise.brain.task.wage.service.WageRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工资核算-工资管理（汇总 + 审核）
 */
@RestController
@RequestMapping("/api/v1/wage/records")
@Tag(name = "工资核算-工资管理")
public class WageRecordController {

    @Resource
    private WageRecordService recordService;

    /** 工资汇总（按 工资月份 + 工号） */
    @GetMapping("/summary")
    public Result<List<WageSummaryVO>> summary(@RequestParam(required = false) String month,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String auditStatus) {
        return Result.success(recordService.summary(month, keyword, auditStatus));
    }

    /** 单条审核 / 驳回 */
    @PostMapping("/audit")
    public Result<WageRecord> auditOne(@Valid @RequestBody WageAuditRequest request) {
        boolean approved = !Boolean.FALSE.equals(request.getApproved());
        recordService.auditOne(request);
        return Result.success(approved ? "审核通过" : "已驳回", null);
    }

    /** 批量审核通过（当前筛选范围） */
    @PostMapping("/audit-batch")
    public Result<Map<String, Integer>> auditBatch(@Valid @RequestBody WageAuditRequest request) {
        int n = recordService.auditBatch(request);
        return Result.success("已审核 " + n + " 条", Map.of("count", n));
    }
}
