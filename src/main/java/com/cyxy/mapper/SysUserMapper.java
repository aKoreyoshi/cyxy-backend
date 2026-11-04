package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.SysUser;

/**
 * @author: Koreyoshi
 * 用户表 CRUD 全部由 BaseMapper 提供，不用写 XML。
 * Main.java 上已有 @MapperScan("com.cyxy.mapper")，所以这里不用加 @Mapper。
 * 认人一律用 selectOne(wrapper)，别用 selectById —— 项目踩过一级缓存
 * 返回同一实例的坑（见项目 MEMORY.md）。
 * @version: 1.0
 * @date: 2026年11月04日, 16:59:55
 */

public interface SysUserMapper extends BaseMapper<SysUser> {
}