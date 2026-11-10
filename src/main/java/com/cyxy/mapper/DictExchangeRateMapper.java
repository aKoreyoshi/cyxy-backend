package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.DictExchangeRate;
import org.apache.ibatis.annotations.Insert;

/**
 * @author: Koreyoshi
 * @description: 汇率时序 Mapper，对应 dict_exchange_rate 表
 * 需要写入（拉取最新汇率入库）与按日期区间查询趋势，故单独建接口
 * @version: 1.0
 * @date: 2026年10月31日, 01:28:00
 */
public interface DictExchangeRateMapper extends BaseMapper<DictExchangeRate> {

    /**
     * 一天一条：按 (currency_pair, rate_date) 唯一键写入，同日重复同步则覆盖。
     *
     * <p>为什么不用 MyBatis-Plus 的「先 selectOne 再决定 insert/update」：实体带 @TableLogic，
     * selectOne 会自动追加 is_deleted=0，软删过的行查不到 → 走 insert → 撞唯一键报错。
     * 原生 ON DUPLICATE KEY UPDATE 同时把 is_deleted 复位为 0，天然绕开这个坑
     * （与 product_fba_fee 的 upsert 是同一套写法）。</p>
     *
     * <p>唯一键里的 rate_date 不更新：能撞上就说明两边日期本来就相等。</p>
     */
    @Insert("""
            INSERT INTO dict_exchange_rate
              (currency_pair, rate, source, provider, rate_date, fetched_at, remark, is_deleted)
            VALUES
              (#{currencyPair}, #{rate}, #{source}, #{provider}, #{rateDate}, #{fetchedAt}, #{remark}, 0)
            ON DUPLICATE KEY UPDATE
              rate       = VALUES(rate),
              source     = VALUES(source),
              provider   = VALUES(provider),
              fetched_at = VALUES(fetched_at),
              remark     = VALUES(remark),
              is_deleted = 0
            """)
    void upsert(DictExchangeRate row);
}
