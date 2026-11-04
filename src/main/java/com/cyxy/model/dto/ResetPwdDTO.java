package com.cyxy.model.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 超管在「用户管理」页给任意用户重置密码。
 *
 * 为什么不需要 oldPwd：超管本来就不该知道对方的旧密码，
 * 而且这个接口只在 /api/system/** 下、由拦截器限定超管可用。
 * 自己改自己的密码走 ChangePwdDTO，那个必须验旧密码。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ResetPwdDTO(Long userId, String newPwd) {
}
