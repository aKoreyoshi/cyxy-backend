package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.Product;

/**
 * @author: Koreyoshi
 * @description: 产品主数据 Mapper，对应 product 表
 * 单表 CRUD 由 BaseMapper 提供，复杂查询（列表分页、按名称远程搜索）在此扩展
 * @version: 1.0
 * @date: 2026年10月31日, 01:28:00
 */
public interface ProductMapper extends BaseMapper<Product> {
}
