package com.cyxy.common;

/**
 * @author: Koreyoshi
 * @description: 状态码枚举
 * @version: 1.0
 * @date: 2026年10月27日, 22:57:13
 */
public enum ResultCode {

    SUCCESS(200, "成功"),
    BIZ_ERROR(500, "业务异常"),
    NOT_LOGIN(401, "未登录或登录已过期"),
    NO_PERMISSION(403, "无权限"),
    PARAM_ERROR(400, "参数错误");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

}