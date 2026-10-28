package com.cyxy.exception;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年10月27日, 23:00:39
 */
public class CYXYException extends RuntimeException{

    private final int code;

    public CYXYException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public CYXYException(String msg) {
        this(500, msg);
    }

    public int getCode() {
        return code;
    }
}