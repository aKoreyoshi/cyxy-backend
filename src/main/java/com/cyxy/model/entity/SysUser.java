package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年11月04日, 16:35:01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity{

    // 继承：有 is_deleted，软删自动生效
    /** 登录账号，库上有唯一索引 uk_sys_user_username */
    private String username;

    /** BCrypt 密文。绝不出现在任何返回给前端的 VO 里 */
    private String passwordHash;

    /** 姓名 / 昵称 */
    private String realName;

    /** 1 启用 / 0 禁用。禁用后拦截器立刻拒掉，不用等 token 自然过期 */
    private Integer status;

    /**
     * 当前有效令牌。为什么不另建第 6 张 token 表：3 个人用、单机没 Redis，
     * 一人一令牌存在用户表里最省事；副作用是异地登录会顶掉旧令牌，正好当安全特性。
     */
    private String token;

    /** 过期时间，NULL 或早于当前时间都视为失效 */
    private LocalDateTime tokenExpireTime;

    /** 最后登录时间，登录成功时更新 */
    private LocalDateTime lastLoginTime;

    private String remark;
}