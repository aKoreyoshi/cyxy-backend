package com.cyxy.model.vo;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * @author: Koreyoshi
 * @description:
 * 「当前登录用户」返回体。
 * 为什么单独做 VO、不直接返回 SysUser：实体里有 passwordHash 和 token，
 * 一旦直接序列化出去，等于把密码密文和有效令牌一起交给浏览器。
 * @version: 1.0
 * @date: 2026年11月04日, 17:18:34
 */

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record CurrentUserVO(Long id, String username, String realName, String roleCode, String roleName,
                            boolean superAdmin) {
    // 前端据此决定要不要显示「系统管理」入口（后端仍会再校验一次）
}