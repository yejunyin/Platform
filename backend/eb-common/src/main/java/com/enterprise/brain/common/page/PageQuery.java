package com.enterprise.brain.common.page;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询参数基类
 */
@Data
@Schema(description = "分页查询参数")
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码", example = "1")
    @Min(value = 1, message = "页码不能小于1")
    private Long pageNum = 1L;

    @Schema(description = "每页大小", example = "10")
    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 100, message = "每页大小不能超过100")
    private Long pageSize = 10L;

    @Schema(description = "排序字段", example = "createTime")
    private String orderBy;

    @Schema(description = "排序方式: asc升序, desc降序", example = "desc")
    private String orderDirection = "desc";

    /**
     * 获取MyBatis-Plus分页偏移量
     */
    public long getOffset() {
        return (pageNum - 1) * pageSize;
    }

    /**
     * 是否有排序字段
     */
    public boolean hasOrder() {
        return orderBy != null && !orderBy.isBlank();
    }
}
