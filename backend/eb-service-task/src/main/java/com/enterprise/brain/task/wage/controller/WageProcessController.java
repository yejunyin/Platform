package com.enterprise.brain.task.wage.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wage.entity.WageProcess;
import com.enterprise.brain.task.wage.service.WageProcessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工资核算-工序基础资料
 */
@RestController
@RequestMapping("/api/v1/wage/process")
@Tag(name = "工资核算-工序基础资料")
public class WageProcessController {

    @Resource
    private WageProcessService processService;

    @GetMapping
    public Result<List<WageProcess>> list(@RequestParam(required = false) String keyword) {
        return Result.success(processService.list(keyword));
    }

    @PostMapping
    public Result<WageProcess> save(@RequestBody WageProcess process) {
        return Result.success("保存成功", processService.save(process));
    }

    @PutMapping("/{id}")
    public Result<WageProcess> update(@PathVariable Long id, @RequestBody WageProcess process) {
        process.setId(id);
        return Result.success("保存成功", processService.save(process));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        processService.remove(id);
        return Result.success("删除成功", Boolean.TRUE);
    }
}
