package com.enterprise.brain.common.exception;

import com.enterprise.brain.common.result.Result;
import com.enterprise.brain.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 | {} | 原因: {}", request.getRequestURI(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                               HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验异常 | {} | 原因: {}", request.getRequestURI(), msg);
        return Result.validateError(msg);
    }

    /**
     * 处理参数绑定异常（表单提交）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定异常 | {} | 原因: {}", request.getRequestURI(), msg);
        return Result.validateError(msg);
    }

    /**
     * 处理路径参数/请求参数校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e,
                                                            HttpServletRequest request) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验异常 | {} | 原因: {}", request.getRequestURI(), msg);
        return Result.validateError(msg);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e,
                                                                       HttpServletRequest request) {
        String msg = "缺少必要请求参数: " + e.getParameterName();
        log.warn("缺少参数 | {} | {}", request.getRequestURI(), msg);
        return Result.validateError(msg);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
                                                                   HttpServletRequest request) {
        String msg = String.format("参数类型不匹配: %s", e.getName());
        log.warn("参数类型错误 | {} | {}", request.getRequestURI(), msg);
        return Result.validateError(msg);
    }

    /**
     * 处理请求体不可读异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e,
                                                               HttpServletRequest request) {
        log.warn("请求体不可读 | {} | 原因: {}", request.getRequestURI(), e.getMessage());
        return Result.validateError("请求体格式错误");
    }

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e,
                                                                      HttpServletRequest request) {
        String msg = String.format("不支持的请求方法: %s", e.getMethod());
        log.warn("请求方法错误 | {} | {}", request.getRequestURI(), msg);
        return Result.error(ResultCode.METHOD_NOT_ALLOWED, msg);
    }

    /**
     * 处理所有其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 | {} | 堆栈信息: ", request.getRequestURI(), e);
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
    }
}
