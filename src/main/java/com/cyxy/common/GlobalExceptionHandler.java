package com.cyxy.common;

import com.cyxy.exception.CYXYException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author: Koreyoshi
 * @description: 全局异常处理
 * @version: 1.0
 * @date: 2026年10月27日, 22:59:23
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 业务异常：自己主动 throw 的，比如"用户名已存在"
    @ExceptionHandler(CYXYException.class)
    public Result<Void> handleBiz(CYXYException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 兜底：没预料到的异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        return Result.fail(500, "服务器开小差了：" + e.getMessage());
    }
}