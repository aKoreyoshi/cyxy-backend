package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.SysUserRole;

/**
 * @author: Koreyoshi
 * 用户-角色关联。换角色时 delete(wrapper) 物理删旧关联再插新的，不涉及软删。
 * @version: 1.0
 * @date: 2026年11月04日, 17:02:25
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}