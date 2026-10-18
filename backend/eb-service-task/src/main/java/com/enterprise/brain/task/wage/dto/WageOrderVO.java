package com.enterprise.brain.task.wage.dto;

import com.enterprise.brain.task.wage.entity.WageOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 生产任务单列表行（带小组名称）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工资核算-任务单列表行")
public class WageOrderVO extends WageOrder {

    @Schema(description = "加工组别名称")
    private String groupName;
}
