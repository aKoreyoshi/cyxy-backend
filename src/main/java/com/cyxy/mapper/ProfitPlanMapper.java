package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.ProfitPlan;

/**
 * @author: Koreyoshi
 * @description: 利润测算方案 Mapper，对应 profit_plan 表
 * 列表页需要显示产品名称时再在此写连表查询，其余用 BaseMapper 即可
 * @version: 1.0
 * @date: 2026年10月31日, 01:28:00
 */
public interface ProfitPlanMapper extends BaseMapper<ProfitPlan> {
}
