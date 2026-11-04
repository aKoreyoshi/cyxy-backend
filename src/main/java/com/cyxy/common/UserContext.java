package com.cyxy.common;

/**
 * @author: Koreyoshi
 * @description: 把当前登录用户挂在线程上，Controller / Service 随时能取，不用层层传参。
 * ⚠️ 必须清：Tomcat 线程是复用的，不清的话下一个请求可能读到上一个用户的信息（串号）。
 * 正常路径由拦截器的 afterCompletion 清；但 preHandle 返回 false 的拦截器，
 * Spring 规范里不会触发它自己的 afterCompletion，所以 deny() 里还得主动清一次。
 * @version: 1.0
 * @date: 2026年11月04日, 17:24:31
 */
public final class UserContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }        // 纯工具类，禁止 new

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}