package com.cyxy.common;

/**
 * @author: Koreyoshi
 * @description: 统一返回结构类
 * @version: 1.0
 * @date: 2026年10月27日, 22:51:49
 */
public record Result<T>(int code, String msg, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

}