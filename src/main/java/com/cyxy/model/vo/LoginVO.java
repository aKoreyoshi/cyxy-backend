package com.cyxy.model.vo;

/**
 * @author: Koreyoshi
 * @description: 登录成功返回结果
 * @version: 1.0
 * @date: 2026年10月27日, 23:40:00
 */
public record LoginVO(String token, UserInfoVO userInfo) {
}
