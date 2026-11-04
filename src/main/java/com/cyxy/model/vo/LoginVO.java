package com.cyxy.model.vo;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * @author: Koreyoshi
 * @description: 登录成功返回结果
 *
 * 一次登录就把「令牌 + 当前用户完整信息」都给前端，避免登录页再多发一次
 * /api/auth/current 请求。刷新页面时前端再用 current 接口覆盖一次本地缓存即可。
 *
 * 为什么把原来的 UserInfoVO 换成 CurrentUserVO：
 *   旧 VO 只有 id / username / nickname，新权限体系需要的 roleCode / roleName /
 *   superAdmin 一个都没有（前端要靠 superAdmin 决定显不显示「系统管理」入口）。
 *   而且旧数据前端存了却没人用，右上角一直写死「管理员」。
 *
 * ⚠️ 字段名是 user（不是 userInfo）：前端 login.vue 和 stores/user.js 要同步改成 res.user，
 *   但 store 里 state.userInfo 这个 key 保持不变，这样 persist 和 localStorage 结构不用动。
 *
 * @version: 1.1
 * @date: 2026年11月05日, 00:20:00
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record LoginVO(String token, CurrentUserVO user) {

}
