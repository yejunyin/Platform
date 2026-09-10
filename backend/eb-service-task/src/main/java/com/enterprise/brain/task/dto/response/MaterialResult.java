package com.enterprise.brain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 补退料接口统一响应格式：{ "status": 0, "msg": "success", "data": {} }
 * <p>status: 0成功 2库存不足部分匹配 其他非0失败</p>
 */
@Data
@Schema(description = "补退料接口统一响应")
public class MaterialResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SUCCESS = 0;
    public static final int PARTIAL_MATCH = 2;
    public static final int FAIL = 1;

    @Schema(description = "状态码：0成功 2库存不足部分匹配 其他非0失败", example = "0")
    private Integer status;

    @Schema(description = "消息", example = "success")
    private String msg;

    @Schema(description = "数据")
    private T data;

    public static <T> MaterialResult<T> ok(T data) {
        MaterialResult<T> r = new MaterialResult<>();
        r.status = SUCCESS;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> MaterialResult<T> partial(T data, String msg) {
        MaterialResult<T> r = new MaterialResult<>();
        r.status = PARTIAL_MATCH;
        r.msg = msg;
        r.data = data;
        return r;
    }

    public static <T> MaterialResult<T> error(String msg) {
        MaterialResult<T> r = new MaterialResult<>();
        r.status = FAIL;
        r.msg = msg;
        return r;
    }

    public static <T> MaterialResult<T> error(Integer status, String msg) {
        MaterialResult<T> r = new MaterialResult<>();
        r.status = status;
        r.msg = msg;
        return r;
    }
}
