package com.cyxy.model.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 菜单管理：新建 / 修改菜单。
 *
 * ⚠️ 故意不提供 url / routeName / menuType / writeApiPatterns 这几个字段：
 *   1) 一期不开放「凭空新建可点击页面」—— 前端路由是代码里注册好的，
 *      随便填一个 url 点进去就是白屏。新建一律是 url 为空的分组目录。
 *   2) writeApiPatterns 是权限拦截的依据，绝不能让页面改，
 *      否则把只读角色的写接口清单清空就等于绕过权限。
 *   所以菜单管理一期只开放：改名、排序、启停、换上级、新建目录。
 *
 * id 为空 = 新建；有值 = 修改。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record MenuSaveDTO(
        Long id,
        Long parentId,
        String title,
        String icon,
        Integer sortNo,
        Integer visible
) {
}
