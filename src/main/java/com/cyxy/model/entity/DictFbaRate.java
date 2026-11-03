package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author: Koreyoshi
 * @description: FBA 配送费率矩阵（行式化），对应 dict_fba_rate 表
 * Excel 中的二维表在此拉平为行。查询固定三键组合：
 * tierCode + priceBand + 重量(oz) BETWEEN weightMinOz AND weightMaxOz。
 * 这样设计是为了日后新增价格带时无需改表结构
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_fba_rate")
public class DictFbaRate extends BaseEntity {

    /** 外键，指向 dict_fba_size_tier.tier_code */
    private String tierCode;

    /** 价格带：LOW 小于 $10 / MID $10~50 / HIGH 大于 $50 */
    private String priceBand;

    /** 重量下界 oz（左开） */
    private BigDecimal weightMinOz;

    /** 重量上界 oz（右闭） */
    private BigDecimal weightMaxOz;

    /** 配送费 $/件，不含燃油附加 */
    private BigDecimal rateUsd;

    /** 生效起始日 */
    private LocalDate validFrom;

    /** 生效截止日 */
    private LocalDate validTo;
}
