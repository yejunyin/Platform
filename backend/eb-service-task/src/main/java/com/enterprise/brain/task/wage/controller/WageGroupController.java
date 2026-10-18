package com.enterprise.brain.task.wage.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wage.entity.WageGroup;
import com.enterprise.brain.task.wage.service.WageGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工资核算-人员小组
 */
@RestController
@RequestMapping("/api/v1/wage/groups")
@Tag(name = "工资核算-人员小组")
public class WageGroupController {

    @Resource
    private WageGroupService groupService;

    @GetMapping
    public Result<List<WageGroup>> list() {
        return Result.success(groupService.list());
    }

    @PostMapping
    public Result<WageGroup> save(@RequestBody WageGroup group) {
        return Result.success("保存成功", groupService.save(group));
    }

    @PutMapping("/{id}")
    public Result<WageGroup> update(@PathVariable Long id, @RequestBody WageGroup group) {
        group.setId(id);
        return Result.success("保存成功", groupService.save(group));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        groupService.remove(id);
        return Result.success("删除成功", Boolean.TRUE);
    }
}
