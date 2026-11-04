package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年11月04日, 16:38:55
 */

@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    // 不继承 BaseEntity：此表没有 is_deleted 列
    private Long roleId;

    private Long menuId;

    private Integer permType;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}