package com.cyxy.model.vo;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 角色详情页里的「一行菜单」。
 *
 * permType 为 null 表示这个角色没有勾选该菜单；1 可编辑 / 0 只读。
 * hasWriteApi 告诉前端这一行有没有写操作：没有的（首页 / 参数列表 / 字典）
 * 只读开关要置灰并显示「无写操作」，免得设了半天发现没变化。
 *
 * 用平铺 List 而不是树：前端 el-tree 自己按 parentId 建树更顺手，
 * 也避免后端再套一层树结构 DTO。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record MenuPermItemVO(
        Long menuId,
        Long parentId,
        String title,
        Integer menuType,
        Integer sortNo,
        Integer permType,
        boolean hasWriteApi
) {
}
