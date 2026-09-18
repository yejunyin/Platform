package com.enterprise.brain.task.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.dto.request.QcStaffDingdingIdRequest;
import com.enterprise.brain.task.dto.request.QcStaffSaveRequest;
import com.enterprise.brain.task.dto.response.QcStaffVO;
import com.enterprise.brain.task.service.QcStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 质检人员维护接口（质量管理-质检人员维护，数据源 sys_user：username=工号，real_name=姓名）
 */
@RestController
@RequestMapping("/api/v1/qc/staff")
@Tag(name = "质检人员维护", description = "质检人员名单的查询/新增/删除（sys_user）")
public class QcStaffController {

    @Resource
    private QcStaffService qcStaffService;

    @GetMapping
    @Operation(summary = "质检人员列表", description = "查询未删除人员；keyword 可按工号/姓名模糊检索，不传查全部")
    public Result<List<QcStaffVO>> list(
            @Parameter(description = "工号/姓名关键字") @RequestParam(required = false) String keyword) {
        return Result.success(qcStaffService.list(keyword));
    }

    @PostMapping
    @Operation(summary = "新增质检人员", description = "按工号+姓名新增；工号唯一，同工号曾删除时自动恢复；默认密码 123456")
    public Result<QcStaffVO> add(@Valid @RequestBody QcStaffSaveRequest request) {
        return Result.success("新增成功", qcStaffService.add(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除质检人员", description = "软删除（deleted=1），删除后同工号可再次添加")
    public Result<Boolean> delete(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id) {
        qcStaffService.delete(id);
        return Result.success("删除成功", Boolean.TRUE);
    }

    @PutMapping("/{id}/dingding-id")
    @Operation(summary = "维护钉钉ID", description = "更新指定人员的钉钉ID（dingdingid），传空可清空绑定")
    public Result<QcStaffVO> updateDingdingId(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id,
            @Valid @RequestBody QcStaffDingdingIdRequest request) {
        return Result.success("保存成功", qcStaffService.updateDingdingId(id, request.getDingdingId()));
    }
}
