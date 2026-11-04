package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.SysRoleMenu;

/**
 * @author: Koreyoshi
 * 角色-菜单权限。保存勾选 = 先删后插，由 RoleService 的 @Transactional 保证原子性。
 * @version: 1.0
 * @date: 2026年11月04日, 17:03:10
 */
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
}