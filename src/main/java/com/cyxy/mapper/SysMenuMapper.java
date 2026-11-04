package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.SysMenu;

/**
 * @author: Koreyoshi
 * 菜单表 CRUD。拦截器每次请求会查一次「带写接口清单的菜单」，
 * 量很小（十来行），后面要优化可以加缓存，一期先直查。
 * @version: 1.0
 * @date: 2026年11月04日, 17:01:38
 */

public interface SysMenuMapper extends BaseMapper<SysMenu> {
}