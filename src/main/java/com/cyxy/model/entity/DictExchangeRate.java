package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description: 汇率时序表，对应 dict_exchange_rate 表
 * 与静态配置不同，汇率是时序数据，必须保留历史才能画趋势曲线。
 * currencyPair + rateDate 组成唯一键，一天一条，重复获取则覆盖
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_exchange_rate")
public class DictExchangeRate extends BaseEntity {

    /** 货币对，默认 USD/CNY */
    private String currencyPair;

    /** 汇率值，必须用 BigDecimal，禁用 float/double */
    private BigDecimal rate;

    /** 来源：API 接口拉取 / MANUAL 手工录入 */
    private String source;

    /** 具体数据提供方，如 frankfurter */
    private String provider;

    /** 业务日期，与 currencyPair 组成唯一键 */
    private LocalDate rateDate;

    /** 实际获取时间戳 */
    private LocalDateTime fetchedAt;

    /** 备注 */
    private String remark;
}
