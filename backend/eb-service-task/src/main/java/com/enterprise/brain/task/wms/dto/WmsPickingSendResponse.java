package com.enterprise.brain.task.wms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * WMS 接口响应
 * { "Code": 200, "Success": true, "Message": "领料申请接收成功",
 *   "Data": { "RequestId": "...", "BillCode": "...", "WmsTaskNo": null } }
 *
 * WMS 返回 key 全是大写开头，必须用 @JsonProperty 显式映射
 */
@Data
@Schema(description = "WMS 接口响应")
public class WmsPickingSendResponse {

    @JsonProperty("Code")
    @Schema(description = "业务码：200 成功")
    private Integer code;

    @JsonProperty("Success")
    @Schema(description = "是否成功")
    private Boolean success;

    @JsonProperty("Message")
    @Schema(description = "消息")
    private String message;

    @JsonProperty("Data")
    private RespData data;

    @Data
    public static class RespData {
        @JsonProperty("RequestId")
        private String requestId;
        @JsonProperty("BillCode")
        private String billCode;
        @JsonProperty("WmsTaskNo")
        private String wmsTaskNo;
    }
}
