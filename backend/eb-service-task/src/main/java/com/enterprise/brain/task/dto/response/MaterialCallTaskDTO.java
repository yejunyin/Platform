package com.enterprise.brain.task.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 物料呼叫-生产任务响应 DTO
 * <p>字段以 snake_case 输出，直接匹配前端 MaterialCall 的 Task 接口。</p>
 */
@Data
@Schema(description = "物料呼叫-生产任务")
public class MaterialCallTaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("id")
    @Schema(description = "任务单号（pcode）")
    private String id;

    @JsonProperty("order_no")
    @Schema(description = "订单号（ordercode）")
    private String orderNo;

    @JsonProperty("product_code")
    @Schema(description = "产品/物料编码（materialid）")
    private String productCode;

    @JsonProperty("product_name")
    @Schema(description = "产品名称（materialname）")
    private String productName;

    @JsonProperty("quantity")
    @Schema(description = "数量（total）")
    private Integer quantity;

    @JsonProperty("unit")
    @Schema(description = "单位（MES 无此列，预留空）")
    private String unit;

    @JsonProperty("workshop")
    @Schema(description = "车间（dept）")
    private String workshop;

    @JsonProperty("line")
    @Schema(description = "产线（machcode）")
    private String line;

    @JsonProperty("planned_start")
    @Schema(description = "计划开始（starttime，格式 yyyy-MM-dd HH:mm）")
    private String plannedStart;

    @JsonProperty("planned_end")
    @Schema(description = "计划完成（MES 无此列，预留空）")
    private String plannedEnd;

    @JsonProperty("status")
    @Schema(description = "任务状态：pending/preparing/ready/calling，默认 pending")
    private String status;

    @JsonProperty("schedulepriority")
    @Schema(description = "排程优先级（MES 原值，1=最高 4=低）")
    private Integer schedulepriority;

    @JsonProperty("spec")
    @Schema(description = "规格（spec）")
    private String spec;

    @JsonProperty("staffname")
    @Schema(description = "负责人（staffname）")
    private String staffname;
}
