package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description: 利润计算结果快照，对应 profit_calc_result 表
 * 与前端 calc.js 的 calculate() 返回值逐列对应，可用于 Java 侧的回归比对。
 * 写入后不再更新；输入项在此冗余保存，确保历史结果可脱离 product / profit_plan 独立复现
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("profit_calc_result")
public class ProfitCalcResult extends BaseEntity {

    /** 所属方案 profit_plan.id */
    private Long planId;

    /** 关联产品（冗余，便于按产品查历史） */
    private Long productId;

    /** 计算时间 */
    private LocalDateTime calcAt;

    /** 命中的字典版本 / 生效日，便于追溯 */
    private String dictVersion;

    /** 规范化后长度 cm */
    private BigDecimal sizeCmL;

    /** 规范化后宽度 cm */
    private BigDecimal sizeCmW;

    /** 规范化后高度 cm */
    private BigDecimal sizeCmH;

    /** 换算后长度 inch */
    private BigDecimal sizeInchL;

    /** 换算后宽度 inch */
    private BigDecimal sizeInchW;

    /** 换算后高度 inch */
    private BigDecimal sizeInchH;

    /** 抛货体积重 kg（长×宽×高cm ÷ 材积比） */
    private BigDecimal volWeightKg;

    /** 抛货体积重 lb（页面双单位展示） */
    private BigDecimal volWeightLb;

    /** 产品实重 lb */
    private BigDecimal weightLb;

    /** 体积重 lb（长×宽×高in ÷ 139） */
    private BigDecimal dimWeightLb;

    /** 计费重 lb（实重与体积重取大） */
    private BigDecimal billWeightLb;

    /** 计费依据：actual 按实重 / dimensional 按体积重 */
    private String billWeightBasis;

    /** 头程运费 ￥ */
    private BigDecimal firstLegCostCny;

    /** 尺寸分段：small / large / oversize */
    private String sizeTierKey;

    /** 尺寸分段中文名 */
    private String sizeTierLabel;

    /** 价格带：low / mid / high */
    private String priceBand;

    /** 价格带显示文案 */
    private String priceBandLabel;

    /** 尾程配送费 $（未含燃油附加） */
    private BigDecimal fbaFeeBeforeFuelUsd;

    /** 尾程配送费 $（已含 3.5% 燃油附加） */
    private BigDecimal fbaFeeUsd;

    /** 命中的佣金类目编码 */
    private String commissionCategoryCode;

    /** 实际佣金率 */
    private BigDecimal commissionRate;

    /** 佣金金额 $，阶梯佣金改为金额口径后以此为准 */
    private BigDecimal commissionAmountUsd;

    /** 类目退货阈值 */
    private BigDecimal returnThreshold;

    /** 退货率 */
    private BigDecimal returnRate;

    /** 退货阈值差 = MAX(0, 退货率 − 阈值) */
    private BigDecimal thresholdDiff;

    /** 平均退货费用 ￥ */
    private BigDecimal returnCostCny;

    /** 采购成本 ￥（输入冗余） */
    private BigDecimal purchaseCost;

    /** 合仓费 $（输入冗余） */
    private BigDecimal storageFeeUsd;

    /** 尾程退货费 $（输入冗余） */
    private BigDecimal returnFeeUsd;

    /** 广告占比 %（输入冗余） */
    private BigDecimal adRate;

    /** VAT 占比 %（输入冗余） */
    private BigDecimal vatRate;

    /** 其他杂费占比 %（输入冗余） */
    private BigDecimal miscRate;

    /** 本次计算所用汇率，必须固化，否则历史结果会随实时汇率漂移 */
    private BigDecimal exchangeRate;

    /** 售价 $（输入冗余） */
    private BigDecimal salePriceUsd;

    /** 到手收入 ￥ */
    private BigDecimal revenueCny;

    /** 产品总成本 ￥ */
    private BigDecimal totalCostCny;

    /** 实际垫资成本 ￥ */
    private BigDecimal advanceCostCny;

    /** FBA 费用合计 ￥ */
    private BigDecimal fbaTotalCny;

    /** 利润 ￥ */
    private BigDecimal profitCny;

    /** 利润率，0~1 小数，页面乘 100 展示 */
    private BigDecimal marginRate;

    /** 目标利润率 % */
    private BigDecimal targetMargin;

    /** 判定结果：good / warn / danger */
    private String marginStatus;
}
