package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年11月04日, 16:58:49
 */

@Data
@TableName("sys_user_role")
public class SysUserRole {

    private Long userId;
    private Long roleId;
    private LocalDateTime createdTime;
}