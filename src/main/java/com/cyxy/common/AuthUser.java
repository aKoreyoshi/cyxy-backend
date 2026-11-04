package com.cyxy.common;

import com.cyxy.model.entity.SysRole;
import com.cyxy.model.entity.SysUser;

/**
 * @author: Koreyoshi
 * @description:
 * 一次请求里的登录上下文：当前用户 + 他的角色。
 * 用 record 就够，整条链路只读，不需要可变对象。
 * @version: 1.0
 * @date: 2026年11月04日, 17:21:44
 */
public record AuthUser(SysUser user, SysRole role) {

    /** 内置超管判定抽成方法，免得拦截器、菜单树、各 Service 里重复写 null 判断 */
    public boolean isSuperAdmin() {
        return role != null && Integer.valueOf(1).equals(role.getIsBuiltin());
    }
}