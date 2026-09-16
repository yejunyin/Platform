package com.enterprise.brain.task.dto.mq;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 设备维修工单 MQ 消息顶层结构
 * <p>请求体与 MQ 消息体同构：校验通过后原样序列化发送至 MQ。</p>
 * <p>注意：data 字段保持默认序列化（DELETE 时输出 null），不使用 NON_NULL。</p>
 */
@Data
@Schema(description = "设备维修工单MQ消息（WorkOrderEvent包装类）")
public class WorkOrderEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "操作类型：UPSERT(插入/更新) / DELETE(删除)，大小写不敏感", example = "UPSERT")
    private String op;

    @Schema(description = "源系统工单ID（幂等键）", example = "24501")
    private String sourceId;

    @Schema(description = "工单业务数据；op=UPSERT 必填，op=DELETE 为 null")
    private WorkOrderDTO data;
}
