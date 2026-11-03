package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.ProfitCalcResult;

/**
 * @author: Koreyoshi
 * @description: 利润计算结果快照 Mapper，对应 profit_calc_result 表
 * 写入后不再更新，主要操作是插入与按方案查询历史
 * @version: 1.0
 * @date: 2026年10月31日, 01:28:00
 */
public interface ProfitCalcResultMapper extends BaseMapper<ProfitCalcResult> {
}
