package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.SysRole;

/**
 * @author: Koreyoshi
 * @version: 1.0
 * @date: 2026年11月04日, 17:00:57
 */

/** 角色表 CRUD。按 roleCode 查内置超管时用 LambdaQueryWrapper，同样避开 selectById。 */
public interface SysRoleMapper extends BaseMapper<SysRole> {
}