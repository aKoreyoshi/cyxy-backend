package com.cyxy.model.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 角色管理：新建 / 修改角色，并把菜单勾选一起提交。
 *
 * 为什么把 menus 合进同一个 DTO、不再单独做 /save-menus 接口：
 * 「角色建好了但权限没存上」是最烦人的半截状态。一次请求一个事务，
 * 要么全成功要么全回滚，前端一个表单点一次确定就完事。
 *
 * id 为空 = 新建；有值 = 修改。内置超管角色（isBuiltin=1）不允许提交到这里。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record RoleSaveDTO(
        Long id,
        String roleCode,
        String roleName,
        Integer sortNo,
        Integer status,
        String remark,
        List<MenuPermDTO> menus
) {
}
