package com.cyxy.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年11月03日, 23:17:36
 */

@Data
public class ProfitCalcFormDTO {

    private Long productId;              // 可空：没选产品
    private String planName;
    private String sizeUnit = "cm";
    private BigDecimal length, width, height;
    private BigDecimal weightKg;         // 含包装实重（前端标签改，字段名不动）
    private BigDecimal purchaseCost;
    private BigDecimal adRate, vatRate, miscRate;      // 百分制：15 = 15%
    private Integer volumeDivisor;
    private String logisticsProvider;
    private BigDecimal firstLegPrice;

    /** 配送方式：AMAZON_SEND / SELF_CARRIER；空值按 SELF_CARRIER（历史兼容） */
    private String shippingMethod;

    /** 头程方式 → dict_first_leg_method，仅记录用，不直接参与计算 */
    private String firstLegMethodCode;

    /** 配送总费用 $（整票货件），仅 AMAZON_SEND 参与计算 */
    private BigDecimal shippingTotalFeeUsd;

    /** 商品总数（件），仅 AMAZON_SEND 参与计算 */
    private Integer totalUnits;

    private String commissionCategory;                 // 用户手选，不自动带出
    private String returnCategory;                     // 可从产品带出
    private BigDecimal returnRate;                     // 百分制
    private BigDecimal returnFeeUsd;
    private BigDecimal storageFeeUsd;                  // 合仓费
    private BigDecimal inboundConfigFeeUsd;           // 新增，默认填 0
    private BigDecimal monthlyStorageUsd;             // 新增，默认填 0
    private BigDecimal salePriceUsd;
    private BigDecimal exchangeRate;
    private BigDecimal targetMargin;
    private Boolean useMinCommission;
    private String remark;
}