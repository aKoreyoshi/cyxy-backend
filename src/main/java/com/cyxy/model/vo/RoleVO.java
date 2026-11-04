package com.cyxy.model.vo;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 角色管理列表的返回体。
 * isBuiltin=1 是内置超管，前端据此把「编辑 / 删除」按钮置灰。
 * 后端 SysRoleService 里也会再挡一次，不依赖前端自觉。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record RoleVO(
        Long id,
        String roleCode,
        String roleName,
        Integer isBuiltin,
        Integer sortNo,
        Integer status,
        String remark
) {
}
