package com.cyxy.model.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * @author: Koreyoshi
 * @description:
 * 修改自己的密码。
 * 为什么必须验旧密码：这个接口对所有登录用户开放（不只超管），
 * 不验的话，别人借你没锁屏的浏览器就能直接把密码改掉。
 * 超管在用户管理页「重置密码」不走这个 DTO —— 那个场景本来就不该知道对方旧密码。
 * @version: 1.0
 * @date: 2026年11月04日, 17:15:26
 */

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ChangePwdDTO(String oldPwd, String newPwd) {
}