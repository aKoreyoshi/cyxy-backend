package com.cyxy.model.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 「当前生效汇率」的展示结构，供利润测算页和参数列表汇率页共用。
 *
 * <p>⚠️ rate 来自 sys_config.DEFAULT_EXCHANGE_RATE（业务读取的唯一入口），
 * 而 rateDate / source / provider / fetchedAt 来自 dict_exchange_rate 最新一条历史记录，
 * 只用于告诉用户「这个值什么时候、从哪儿来的」。<b>不要反过来用历史表的值覆盖 rate</b>，
 * 否则又变成两套口径。</p>
 *
 * <p>全局 Jackson 配了 SNAKE_CASE，序列化后是 rate / rate_date / source /
 * provider / fetched_at / updated_time / stale，前端按 snake_case 取。</p>
 *
 * <p><b>两个时间不是一回事</b>：fetchedAt 是「这条汇率什么时候从外部接口拿回来的」，
 * 属于历史记录；updatedTime 是「全局参数『最新汇率』这一行什么时候被改的」，
 * 属于当前值。页面上的「修改时间 / 更新于」一律用 updatedTime，
 * 因为用户关心的是「我这次获取动作有没有生效、这个当前值有多新」。</p>
 *
 * @param rate        当前生效汇率（1 USD = X CNY）
 * @param rateDate    最新一条历史记录的业务日期；没有历史记录时为 null
 * @param source      最新记录来源：API / MANUAL
 * @param provider    最新记录的数据提供方，如 frankfurter
 * @param fetchedAt   最新记录的实际获取时间（历史表）
 * @param updatedTime 全局参数「最新汇率」这一行的修改时间，取自 sys_config.updated_time
 * @param stale       数据是否可能过期（超过 3 个自然日没更新），前端只做提示、不阻断测算
 */
public record ExchangeRateVO(
        BigDecimal rate,
        LocalDate rateDate,
        String source,
        String provider,
        LocalDateTime fetchedAt,
        LocalDateTime updatedTime,
        boolean stale
) {
}
