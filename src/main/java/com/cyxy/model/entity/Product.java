package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description: 产品主数据，对应 product 表
 * 数据源为公司产品 Excel，后续由系统录入。所含字段均为「产品固有属性」，
 * 不含任何利润测算的中间值与结果（那些由 profit_plan / profit_calc_result 承载）
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    /** 产品编号，唯一 */
    private String productNo;

    /** 产品名称（中文） */
    private String nameCn;

    /** 产品名称（英文） */
    private String nameEn;

    /** 产品主图 URL 或对象存储 Key */
    private String mainImage;

    /**
     * 产品类目编码，指向 dict_category_return_threshold.threshold_code，产品录入下拉与此同源。
     * 注意：与算利润用的 dict_category_commission 是两套独立字典，不联动
     */
    private String categoryCode;

    /** 竞品 ASIN */
    private String competitorAsin;

    /** 1688 采购链接 */
    private String sourceUrl;

    /** 供应商名称 */
    private String supplier;

    /** 采购单价 ￥/件（裸价，不含运费包材） */
    private BigDecimal purchasePrice;

    /** 起订量（件） */
    private Integer moq;

    /** 包材/贴标 ￥/件 */
    private BigDecimal packageCost;

    /** 平均运费 ￥/件 */
    private BigDecimal avgDomesticShipping;

    /** 净重 g（不参与计费） */
    private BigDecimal netWeightG;

    /** 含包装实重 g，FBA 与头程均按此计费，联动带入计算器 */
    private BigDecimal packagedWeightG;

    /** 关税费 ￥/件。当前手工填写；后续可改为 (采购单价 + 包材) × 关税率 自动计算 */
    private BigDecimal tariffFee;

    /** 包装长 cm */
    private BigDecimal lengthCm;

    /** 包装宽 cm */
    private BigDecimal widthCm;

    /** 包装高 cm */
    private BigDecimal heightCm;

    /**
     * 尺寸分段：small 小号标准 / large 大号标准 / oversize 大件超大件
     * 指向 dict_fba_size_tier.tier_code。
     * 由「包装长/宽/高 + 含包装实重」自动匹配得出，不需人工选择；
     * 后台保存时会按字典规则兜底重算，防止前端漏传或篡改
     */
    private String sizeTierCode;

    /** 头程方式，指向 dict_first_leg_method.method_code */
    private String firstLegMethodCode;

    /**
     * 配送方式：AMAZON_SEND 亚马逊SEND / SELF_CARRIER 自己选择承运人。
     * 决定「哪一组费用字段生效」，NULL 视为 SELF_CARRIER（历史数据兼容）。
     *
     * 为什么要按配送方式分成两套费用字段：SEND 的费用由亚马逊后台核算、以美元结算；
     * 自选承运人是自己找物流商结算、以人民币结算，币种和来源都不同。若合并成一个字段，
     * 历史数据就无法判断当初填的是哪一种，所以各占一列、按配送方式二选一。
     */
    private String shippingMethod;

    /** 物流商名称 */
    private String logisticsProvider;

    /** 头程单价 ￥/kg。仅 SELF_CARRIER（自选承运人）模式使用 */
    private BigDecimal firstLegUnitPrice;

    /**
     * 配送总费用 $（当前产品）。仅 AMAZON_SEND 模式有值。
     * 口径：一行 product 只记录一种产品，这里填的是「这个产品」的配送费，不再是整票货件费用；
     * 与它相除的 totalUnits 也是这个产品的件数，分子分母同步从整票缩到单品，
     * 所以「费用 × 汇率 ÷ 件数」的公式不用改，单件头程结果的口径仍然一致。
     * ⚠️ 与 shippingTotalFeeCny 按配送方式二选一，不可混用、也不互相换算
     */
    private BigDecimal shippingTotalFeeUsd;

    /** 商品总数量（件，当前产品）。每行记录单种产品，不记单票货件总数；两种配送方式都填 */
    private Integer totalUnits;

    /**
     * 配送总费用 ￥（当前产品）。仅 SELF_CARRIER（自己选择承运人）模式填写，
     * 与 firstLegUnitPrice（头程单价）同组；AMAZON_SEND 模式应为 NULL。
     *
     * 为什么单独加这一列：自选承运人时既要按「单价 × 计费重」算单件头程，
     * 又要留一份实际结算的总费用（人民币）备查，原来只有单价列，总额无处落库。
     * 本期只做录入与留存，不参与利润计算 —— 计算公式本次不改。
     *
     * ⚠️ 系统里有两个「配送总费用」：本列（￥，自选承运人）与 shippingTotalFeeUsd
     *    （$，亚马逊SEND）。靠列名后缀 cny / usd 区分，前端按配送方式条件显示，
     *    同一时刻只会看到其中一个，填错模式等于把美元当人民币存进去。
     */
    private BigDecimal shippingTotalFeeCny;

    /**
     * 预计送达日期（自然日，精确到天）。
     * 录入或编辑时填写；确认送达后锁定不可改，要改必须先「撤销确认」
     */
    private LocalDate expectedDeliveryDate;

    /**
     * 实际送达时间（点「确认送达」那一刻，服务端按北京时间生成）。
     * 非空即视为已确认，配送相关字段随之锁定；撤销确认时清空
     */
    private LocalDateTime actualDeliveryTime;

    /**
     * 配送状态：NOT_SET / IN_TRANSIT / EARLY / ON_TIME / LATE。
     * 冗余字段，便于列表页直接筛选排序；判定真值始终以两个时间为准
     */
    private String deliveryStatus;

    /**
     * 送达天数差 = 实际送达日期 − 预计送达日期（自然日）。
     * 负数提前几天、0 准时、正数晚几天；未确认送达时为 NULL
     */
    private Integer delayDays;

    /** 定价销售价 $/件 */
    private BigDecimal listPriceUsd;

    /** 竞品售价 $ */
    private BigDecimal competitorPriceUsd;

    /** 预估月销量（件） */
    private Integer monthlySalesEst;

    /** 首批备货量（件） */
    private Integer firstBatchQty;

    /** 状态：DRAFT 待评估 / SELECTED 已选 / OFFLINE 已下架 */
    private String status;

    /** 备注 / 风险 */
    private String remark;
}
