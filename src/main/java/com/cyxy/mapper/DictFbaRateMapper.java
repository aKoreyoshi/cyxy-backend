package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.DictFbaRate;

/**
 * @author: Koreyoshi
 * @description: FBA 配送费率矩阵 Mapper，对应 dict_fba_rate 表
 * 典型查询：tierCode + priceBand + 重量(oz) BETWEEN weightMinOz AND weightMaxOz
 * @version: 1.0
 * @date: 2026年10月31日, 01:36:00
 */
public interface DictFbaRateMapper extends BaseMapper<DictFbaRate> {
}
