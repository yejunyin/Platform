package com.enterprise.brain.task.wage.controller;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.task.wage.entity.WagePrice;
import com.enterprise.brain.task.wage.service.WagePriceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工资核算-单价工序资料
 */
@RestController
@RequestMapping("/api/v1/wage/price")
@Tag(name = "工资核算-单价工序资料")
public class WagePriceController {

    @Resource
    private WagePriceService priceService;

    @GetMapping
    public Result<List<WagePrice>> list(@RequestParam(required = false) String processName,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String keyword) {
        return Result.success(priceService.list(processName, status, keyword));
    }

    @PostMapping
    public Result<WagePrice> save(@RequestBody WagePrice price) {
        return Result.success("保存成功", priceService.save(price));
    }

    @PutMapping("/{id}")
    public Result<WagePrice> update(@PathVariable Long id, @RequestBody WagePrice price) {
        price.setId(id);
        return Result.success("保存成功", priceService.save(price));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        priceService.remove(id);
        return Result.success("删除成功", Boolean.TRUE);
    }
}
