package com.cyxy.model.vo;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 角色详情：基本信息 + 全量菜单勾选情况。
 *
 * menus 返回的是「所有启用菜单」，已勾的带 permType，没勾的 permType=null，
 * 前端直接铺成树让用户勾，不用自己再调一次菜单接口拼数据。
 * 内置超管角色也会返回全量菜单但前端应整页置灰（后端同样拒绝对它做修改）。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record RoleDetailVO(
        Long id,
        String roleCode,
        String roleName,
        Integer isBuiltin,
        Integer sortNo,
        Integer status,
        String remark,
        List<MenuPermItemVO> menus
) {
}
