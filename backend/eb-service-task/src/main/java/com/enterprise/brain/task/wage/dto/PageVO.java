package com.enterprise.brain.task.wage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 内存分页结果（工资模块列表数据量可控，筛选后切片）
 */
@Data
@Schema(description = "分页结果")
public class PageVO<T> {

    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;
    private int totalPages;

    public static <T> PageVO<T> of(List<T> all, int pageNum, int pageSize) {
        PageVO<T> vo = new PageVO<>();
        int safeSize = pageSize <= 0 ? 20 : pageSize;
        int safePage = Math.max(1, pageNum);
        int total = all.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) safeSize));
        if (safePage > pages) {
            safePage = pages;
        }
        int from = (safePage - 1) * safeSize;
        int to = Math.min(from + safeSize, total);
        vo.setList(from >= total ? List.of() : all.subList(from, to));
        vo.setTotal(total);
        vo.setPageNum(safePage);
        vo.setPageSize(safeSize);
        vo.setTotalPages(pages);
        return vo;
    }
}
