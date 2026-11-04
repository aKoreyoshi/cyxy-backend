package com.cyxy.model.vo;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 登录后下发给前端的菜单树节点。
 *
 * 字段名刻意对齐现有 router/menu.json（id / parentId / url / title / icon / menuType），
 * 前端拿到后把「读静态 json」换成「读后端返回值」即可，别的地方不用改。
 *
 * ⚠️ menuType 的语义沿用前端模板：0 显示 / 1 隐藏功能页（不是 0目录/1菜单），
 *    详见 SysMenu 的注释。隐藏页也必须下发，否则 router/index.js 的守卫
 *    会把 /product/detail 这类路径判成 404。
 *
 * permType（1 可编辑 / 0 只读）是额外带的，一期只用于前端提示与置灰；
 * 真正的拦截在后端 AuthInterceptor，前端这个值改了也没用。
 *
 * children 用可变的 ArrayList：建树时要往父节点里挂子节点，List.of() 挂不进去。
 *
 * isBuiltin / routeName 是「菜单管理页」额外要用的：
 *   - isBuiltin=1 的内置菜单后端禁止删除，前端据此把删除按钮置灰，别让人白点一次；
 *   - routeName 是后台稳定标识（SYSTEM_ 前缀就靠它识别系统管理组），
 *     管理页展示出来方便对照，也方便排查「为什么这个菜单没下发」。
 * 这两个字段登录菜单树用不到，多带两个字段前端忽略即可，不影响原有链路。
 *
 * @version: 1.2
 * @date: 2026年11月05日, 00:45:00
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record MenuTreeNodeVO(Long id, Long parentId, String title, String url, String icon,
                             Integer menuType, Integer sortNo, Integer visible,
                             Integer permType, Integer isBuiltin, String routeName,
                             List<MenuTreeNodeVO> children) {
}
