package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.ProductFbaFee;

/**
 * @author: Koreyoshi
 * @description: 产品 FBA 费用 Mapper，对应 product_fba_fee 表
 * 与 product 一一对应，按阶段做部分更新（录入阶段与测算阶段各写一批字段）
 * @version: 1.0
 * @date: 2026年11月1日, 23:40:00
 */
public interface ProductFbaFeeMapper extends BaseMapper<ProductFbaFee> {
}
