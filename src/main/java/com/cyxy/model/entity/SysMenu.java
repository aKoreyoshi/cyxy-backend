package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单 / 权限项 → sys_menu。
 *
 * 一期不开放「凭空新建可点击页面」：没有对应前端 .vue 会白屏，
 * 所以 create 接口只允许建 url 为空的分组目录，url / routeName 强制为空。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 上级菜单 id，0 表示一级菜单 */
    private Long parentId;

    private String title;

    /** 前端已注册的路由名，用于菜单管理里做标识；一期前端实际靠 url 跳转，此列可为空 */
    private String routeName;

    /** hash 路由 path；分组目录为空，前端 store 用 url 是否为空区分目录与可点击页 */
    private String url;

    private String icon;

    /**
     * ⚠️ 语义必须沿用前端 V3S 模板，不能按字面理解成「目录 / 菜单」：
     *   0 = 显示在侧边栏；1 = 隐藏功能页（如 /product/detail）。
     *
     * 为什么：router/menuConfig.js 里 menuKey.menuValue = "0"，
     * hooks/useLayout.js 的 getMenu() 用 menuType == 0 过滤出侧边栏要显示的项。
     * 若后端按「0目录/1菜单」下发，所有可点击页都会被过滤掉，侧边栏只剩空目录。
     *
     * 「是不是目录」不用单独字段：前端 stores/user.js 已有 menuArrWithoutEmptyUrl，
     * 靠 url 是否为空判断即可。
     */
    private Integer menuType;

    private Integer sortNo;

    /** 1 启用 / 0 停用。停用的菜单不下发给任何角色（包括超管），用于菜单管理页的启停开关 */
    private Integer visible;

    /** 1 内置菜单（一期登记的现有页面），禁止删除 */
    private Integer isBuiltin;

    /**
     * 该菜单下需要拦截的写接口，逗号分隔，如 "/api/product/create,/api/product/update"。
     * 为什么不按 HTTP 方法猜「写」：/api/profit/calc 是 POST 但只算不写库，
     * 只读角色应该照样能算；真正要拦的是 /api/profit/save。
     * 无写接口的菜单（首页 / 参数列表 / 字典）保持 NULL，角色页把只读开关置灰。
     */
    private String writeApiPatterns;
}
