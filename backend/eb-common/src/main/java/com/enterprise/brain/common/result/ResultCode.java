package com.enterprise.brain.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 客户端错误 4xx
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "请求资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    VALIDATE_ERROR(422, "参数校验失败"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // 服务端错误 5xx
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    // 业务错误 1xxx
    BUSINESS_ERROR(1000, "业务处理失败"),
    DATA_NOT_EXIST(1001, "数据不存在"),
    DATA_ALREADY_EXIST(1002, "数据已存在"),

    // 任务中心 2xxx
    TASK_NOT_EXIST(2001, "任务不存在"),
    TASK_STATUS_ERROR(2002, "任务状态不允许此操作"),
    TASK_ALREADY_HANDLED(2003, "任务已被处理"),
    TASK_TRANSFER_ERROR(2004, "任务转办失败"),

    // 用户权限 3xxx
    USER_NOT_EXIST(3001, "用户不存在"),
    USER_PASSWORD_ERROR(3002, "用户名或密码错误"),
    USER_DISABLED(3003, "账号已被禁用");

    private final Integer code;
    private final String message;
}
