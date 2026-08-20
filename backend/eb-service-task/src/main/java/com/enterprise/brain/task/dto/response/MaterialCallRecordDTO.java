package com.enterprise.brain.task.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 叫料记录响应 DTO (snake_case 输出，匹配前端 Call 接口)
 */
@Data
@Schema(description = "叫料记录")
public class MaterialCallRecordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("id")
    @Schema(description = "叫料单号")
    private Integer id;

    @JsonProperty("task_id")
    @Schema(description = "任务单号")
    private String taskId;

    @JsonProperty("material_code")
    @Schema(description = "物料编码")
    private String materialCode;

    @JsonProperty("material_name")
    @Schema(description = "物料名称")
    private String materialName;

    @JsonProperty("required_qty")
    @Schema(description = "需求数量")
    private Integer requiredQty;

    @JsonProperty("call_qty")
    @Schema(description = "叫料数量")
    private Integer callQty;

    @JsonProperty("call_type")
    @Schema(description = "叫料类型：normal/urgent")
    private String callType;

    @JsonProperty("caller")
    @Schema(description = "叫料人")
    private String caller;

    @JsonProperty("call_time")
    @Schema(description = "叫料时间(yyyy-MM-dd HH:mm)")
    private String callTime;

    @JsonProperty("status")
    @Schema(description = "叫料状态：pending/delivering/delivered")
    private String status;

    @JsonProperty("responder")
    @Schema(description = "响应人")
    private String responder;

    @JsonProperty("response_time")
    @Schema(description = "响应时间(yyyy-MM-dd HH:mm)")
    private String responseTime;

    @JsonProperty("deliver_time")
    @Schema(description = "送达时间(yyyy-MM-dd HH:mm)")
    private String deliverTime;

    @JsonProperty("remark")
    @Schema(description = "备注")
    private String remark;
}
