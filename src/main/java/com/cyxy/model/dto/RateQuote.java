package com.cyxy.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一次外部汇率接口调用的结果，已经归一成「1 USD = X CNY」。
 *
 * <p>为什么要有这层归一：主源 Frankfurter 返回 {@code {"rate": 6.67, "date": "..."}}，
 * 备源 open.er-api 返回 {@code {"rates": {"CNY": 6.67}, "time_last_update_unix": ...}}，
 * 两家结构、日期字段、精度都不一样。适配层在这里统一掉，后面的落库逻辑就只认一种形状，
 * 将来换第三家数据源也不用改 Service。</p>
 *
 * <p>rateDate 是「业务日期」而不是「抓取时间」：一天一条的唯一键是
 * (currency_pair, rate_date)，用接口给的业务日期才不会把同一天拆成两行。</p>
 *
 * @param rate     1 USD 兑换多少 CNY，统一保留 6 位小数，对应列 DECIMAL(12,6)
 * @param rateDate 业务日期
 * @param provider 数据提供方标识，写入 dict_exchange_rate.provider 便于溯源
 */
public record RateQuote(BigDecimal rate, LocalDate rateDate, String provider) {
}
