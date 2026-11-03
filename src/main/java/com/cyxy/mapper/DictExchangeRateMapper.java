package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.DictExchangeRate;

/**
 * @author: Koreyoshi
 * @description: 汇率时序 Mapper，对应 dict_exchange_rate 表
 * 需要写入（拉取最新汇率入库）与按日期区间查询趋势，故单独建接口
 * @version: 1.0
 * @date: 2026年10月31日, 01:28:00
 */
public interface DictExchangeRateMapper extends BaseMapper<DictExchangeRate> {
}
