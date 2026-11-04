package com.cyxy.model.vo;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;

/**
 * 用户管理列表的返回体。
 *
 * 为什么不能直接返回 SysUser 实体：里面有 passwordHash（密码密文）和 token（有效令牌）。
 * 前者不该出后端，后者等于把别人的登录凭证发给超管的浏览器。
 * 这里只挑列表用得到的字段。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SysUserVO(
        Long id,
        String username,
        String realName,
        Integer status,
        Long roleId,
        String roleName,
        LocalDateTime lastLoginTime,
        String remark,
        LocalDateTime createdTime
) {
}
