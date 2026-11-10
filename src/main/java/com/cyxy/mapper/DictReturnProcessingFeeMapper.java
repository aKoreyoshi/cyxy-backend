package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.DictReturnProcessingFee;

/**
 * @author: Koreyoshi
 * @description: 尾程退货处理费字典 Mapper，对应 dict_return_processing_fee 表
 *
 * <p>不在 SQL 里做区间匹配：字典只有 26 行且长期不变，由 ProfitDictLoader
 * 全量缓存后在内存里定位，既避免 BETWEEN 的边界坑，也方便对 FORMULA 行做续重计算。
 * 参数列表展示页则直接 selectList(null) 取全表。</p>
 */
public interface DictReturnProcessingFeeMapper extends BaseMapper<DictReturnProcessingFee> {
}
