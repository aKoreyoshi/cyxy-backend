package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * @author: Koreyoshi
 * @description: 利润测算方案，对应 profit_plan 表
 * 记录「这一轮打算怎么卖」的输入快照。即使产品档案日后被修改，
 * 本方案也保持录入当时的取值，保证历史方案可原样复算
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("profit_plan")
public class ProfitPlan extends BaseEntity {

    /** 方案编号，唯一 */
    private String planNo;

    /** 方案名称 */
    private String planName;

    /** 关联产品 product.id，可空（纯手填方案） */
    private Long productId;

    /** 来源：PRODUCT 选品带入 / MANUAL 纯手填 */
    private String source;

    /** 尺寸单位 cm / inch */
    private String sizeUnit;

    /** 最长边（用户填的原值，未换算） */
    private BigDecimal length;

    /** 次长边 */
    private BigDecimal width;

    /** 最短边 */
    private BigDecimal height;

    /** 产品重量 kg */
    private BigDecimal weightKg;

    /** 采购/生产成本（含运费）￥，由 采购单价 + 包材贴标 + 平均运费 三项相加得来 */
    private BigDecimal purchaseCost;

    /** 广告费用占比 %，15 表示 15% */
    private BigDecimal adRate;

    /** VAT 占比 % */
    private BigDecimal vatRate;

    /** 其他杂费占比 % */
    private BigDecimal miscRate;

    /** 材积比（抛货），默认由头程方式带出，允许覆盖 */
    private Integer volumeDivisor;

    /** 物流商 */
    private String logisticsProvider;

    /** 头程运费单价 ￥/kg */
    private BigDecimal firstLegPrice;

    /**
     * 配送方式：AMAZON_SEND 亚马逊SEND（按当前产品核算配送费 $）/ SELF_CARRIER 自选承运人。
     * 决定头程运费算法；NULL 视为 SELF_CARRIER（历史方案兼容）
     */
    private String shippingMethod;

    /** 头程方式 → dict_first_leg_method.method_code（记录用） */
    private String firstLegMethodCode;

    /** 配送总费用 $（当前产品，非整票货件），仅 AMAZON_SEND 参与计算 */
    private BigDecimal shippingTotalFeeUsd;

    /** 当前产品的件数（不记单票货件总数），仅 AMAZON_SEND 参与计算 */
    private Integer totalUnits;

    /** 佣金类目，指向 dict_category_commission.category_code */
    private String commissionCategory;

    /** 退货阈值类目，指向 dict_category_return_threshold.threshold_code，不联动，每轮手选 */
    private String returnCategory;

    /** 退货率 % */
    private BigDecimal returnRate;

    /** 尾程退货费 $/件 */
    private BigDecimal returnFeeUsd;

    /** 合仓费 $/件，页面标签写「合仓费」，字段名沿用 storage */
    private BigDecimal storageFeeUsd;

    /** 入库配置费 $/件，计算器「FBA费用计算」卡片手填；参与利润计算，空按 0 处理 */
    private BigDecimal inboundConfigFeeUsd;

    /** 月度仓储费 $/件，计算器「FBA费用计算」卡片手填；参与利润计算，空按 0 处理 */
    private BigDecimal monthlyStorageUsd;

    /** 售价 $ */
    private BigDecimal salePriceUsd;

    /** 汇率快照，本方案采用的值，不随实时汇率变动 */
    private BigDecimal exchangeRate;

    /** 目标利润率 % */
    private BigDecimal targetMargin;

    /** 是否取最低佣金：0 否 / 1 是 */
    private Integer useMinCommission;

    /** 方案备注 */
    private String remark;
}
