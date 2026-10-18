package com.enterprise.brain.task.wage.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wage.dto.WageStaffSaveRequest;
import com.enterprise.brain.task.wage.dto.WageStaffUpdateRequest;
import com.enterprise.brain.task.wage.dto.WageStaffVO;
import com.enterprise.brain.task.wage.service.WageStaffService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工资核算-员工基础资料（复用 sys_user + wage_staff_ext）
 */
@RestController
@RequestMapping("/api/v1/wage/staff")
@Tag(name = "工资核算-员工基础资料")
public class WageStaffController {

    @Resource
    private WageStaffService staffService;

    @GetMapping
    public Result<List<WageStaffVO>> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long groupId,
                                          @RequestParam(required = false) Integer status) {
        return Result.success(staffService.list(keyword, groupId, status));
    }

    @PostMapping
    public Result<WageStaffVO> add(@Valid @RequestBody WageStaffSaveRequest request) {
        return Result.success("新增成功", staffService.add(request));
    }

    @PutMapping("/{userId}")
    public Result<WageStaffVO> update(@PathVariable Long userId,
                                      @Valid @RequestBody WageStaffUpdateRequest request) {
        return Result.success("保存成功", staffService.update(userId, request));
    }

    @DeleteMapping("/{userId}")
    public Result<Boolean> delete(@PathVariable Long userId) {
        staffService.remove(userId);
        return Result.success("删除成功", Boolean.TRUE);
    }
}
