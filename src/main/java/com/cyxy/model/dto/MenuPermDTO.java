package com.cyxy.model.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 角色勾选的一个菜单 + 它的读写标记。
 * permType：1 可编辑 / 0 只读。不出现在列表里的菜单 = 该角色看不到、也用不了。
 *
 * 只读是后端真拦，不是前端把按钮藏起来：AuthInterceptor 命中
 * sys_menu.write_api_patterns 里的写接口时会查这张表，permType=0 直接 403。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record MenuPermDTO(Long menuId, Integer permType) {
}
