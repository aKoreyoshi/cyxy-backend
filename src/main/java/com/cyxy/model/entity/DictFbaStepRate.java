package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author: Koreyoshi
 * @description: FBA 阶梯步进与大件费率，对应 dict_fba_step_rate 表
 * 一张表同时覆盖两种形态：3~20lb 的 STEP_4OZ 步进计费，以及大件的首磅加续磅计费
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_fba_step_rate")
public class DictFbaStepRate extends BaseEntity {

    /** 外键，指向 dict_fba_size_tier.tier_code */
    private String tierCode;

    /** 价格带：LOW / MID / HIGH */
    private String priceBand;

    /** 计费类型：STEP_4OZ 每 4 盎司步进 / FIRST_POUND_PLUS 首磅加续磅 */
    private String rateType;

    /** 适用重量起点 lb */
    private BigDecimal applyFromLb;

    /** 适用重量终点 lb，NULL 表示无上限 */
    private BigDecimal applyToLb;

    /** 起步价 $ */
    private BigDecimal baseUsd;

    /** 每步进单位加价 $ */
    private BigDecimal stepUsd;

    /** 步进单位：OZ4 每 4 盎司 / LB 每磅 */
    private String stepUnit;

    /** 备注（本表后补的列，与其余字典表保持风格一致） */
    private String remark;

    /** 生效起始日 */
    private LocalDate validFrom;

    /** 生效截止日 */
    private LocalDate validTo;
}
