package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description: 产品 FBA 费用，对应 product_fba_fee 表（与 product 一一对应）
 *
 * 存在意义：把「费用类」字段从产品固有属性里剥离出来，product 只描述"这个东西是什么"，
 * 费用统一放这里，随测算进度逐步填充。
 *
 * 填充分两个阶段（都是部分更新，不整条覆盖）：
 *   阶段一 录入产品：只写 inboundConfigFeeUsd / monthlyStorageUsd
 *           —— 这两项只依赖「包装体积 + sys_config 全局参数」，录入时即可确定，其余留空
 *   阶段二 利润测算：回填 fbaFeeUsd / storageFeeUsd / commissionUsd / avgReturnCostCny / fbaTotalCny
 *           —— 这几项依赖售价（佣金、退货费）或方案输入（合仓费），必须测算后才有值
 *
 * 与 profit_calc_result 的分工（两者不是重复）：
 *   product_fba_fee     产品级「当前费用速览」：1:1，会被反复部分更新，反映最新状态
 *   profit_calc_result  方案级「历史快照」：每条写入后不可变，用于多轮方案回溯对比
 *
 * 口径说明：basedPriceUsd 记录依赖售价的费用是基于哪个售价算出的，
 * 定价变更后若不重新测算，页面可据此提示"费用待更新"
 * @version: 1.0
 * @date: 2026年11月1日, 23:40:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_fba_fee")
public class ProductFbaFee extends BaseEntity {

    /** 关联产品 product.id，唯一 */
    private Long productId;

    /* ---------- 阶段一：录入产品时写入 ---------- */

    /** 入库配置费 $/件（Excel AM 列），默认取 sys_config 的 INBOUND_CONFIG_FEE_USD */
    private BigDecimal inboundConfigFeeUsd;

    /** 月度仓储费 $/件（Excel AN 列）= 体积cm³ ÷ 28316.85 × 仓储费率 × 周转月数 */
    private BigDecimal monthlyStorageUsd;

    /* ---------- 阶段二：利润测算后回填 ---------- */

    /** 尾程配送费 $/件，已含 3.5% 燃油附加（对应 calc.js 的 fbaFeeUsd） */
    private BigDecimal fbaFeeUsd;

    /** 合仓费 $/件，手工输入。注意：与入库配置费是两笔不同的费用 */
    private BigDecimal storageFeeUsd;

    /** 佣金 $/件（支持阶梯累进，对应 calc.js 的 commissionAmountUsd） */
    private BigDecimal commissionUsd;

    /** 平均退货费用 ￥/件（对应 calc.js 的 returnCostCny） */
    private BigDecimal avgReturnCostCny;

    /** FBA 总费用 ￥/件（对应 calc.js 的 fbaTotalCny） */
    private BigDecimal fbaTotalCny;

    /* ---------- 口径标注 ---------- */

    /** 上述依赖售价的费用（佣金、平均退货费用等）是基于哪个售价算出的 */
    private BigDecimal basedPriceUsd;

    /** 最后一次测算时间；为空表示尚未测算，仅完成阶段一 */
    private LocalDateTime calcAt;
}
