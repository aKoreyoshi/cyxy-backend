package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.DictFirstLegMethodMapper;
import com.cyxy.mapper.ProductFbaFeeMapper;
import com.cyxy.mapper.ProductMapper;
import com.cyxy.mapper.ProfitCalcResultMapper;
import com.cyxy.mapper.ProfitPlanMapper;
import com.cyxy.model.dto.ProfitCalcFormDTO;
import com.cyxy.model.entity.DictCategoryCommission;
import com.cyxy.model.entity.DictCategoryCommissionTier;
import com.cyxy.model.entity.DictCategoryReturnThreshold;
import com.cyxy.model.entity.DictFirstLegMethod;
import com.cyxy.model.entity.DictFbaRate;
import com.cyxy.model.entity.DictFbaSizeTier;
import com.cyxy.model.entity.DictFbaStepRate;
import com.cyxy.model.entity.Product;
import com.cyxy.model.entity.ProductFbaFee;
import com.cyxy.model.entity.ProfitCalcResult;
import com.cyxy.model.entity.ProfitPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author: Koreyoshi
 * @description: FBA 利润计算器服务
 * calculate() 是前端 calc.js calculate() 的 1:1 Java 翻译：
 * 全程用 double 运算（与 JS Number 同构），出口处转 BigDecimal 存库，
 * 保证与前端逐位对齐 —— 回归基准 24×22.5×2cm / 0.22kg / $18.99 / 6.68
 * → 总成本 ￥95.13 / 利润 ￥31.72 / 利润率 25.0071%。
 * <p>
 * 与 calc.js 的两处刻意差异（均经用户确认）：
 * 1. 入库配置费 / 月度仓储费参与计算：USD 项与佣金、配送费、合仓费同口径进总成本括号 × 汇率，
 *    空值按 0 处理，填 0 时与原公式完全一致；fbaTotalCny 同步计入。
 * 2. 字典来自数据库（ProfitDictLoader），不复制 dict.js 的静态快照。
 * </p>
 * save() 事务内写 profit_plan + profit_calc_result；
 * 仅当选择了产品（productId 非空）时 upsert product_fba_fee，没选产品完全不动那张表。
 * @version: 1.0
 * @date: 2026年11月03日, 23:18:21
 */
@Service
@RequiredArgsConstructor
public class ProfitCalcService {

    /* ---------------- 常量：与前端 dict.js 逐项对齐 ---------------- */

    private static final double CM_TO_INCH = 0.3937008;
    private static final double INCH_TO_CM = 2.54;
    private static final double KG_TO_LB = 2.2046226;
    /** 燃油附加费率 3.5% */
    private static final double FUEL_SURCHARGE = 0.035;
    private static final double OZ_PER_LB = 16;
    /** 体积重除数：长in×宽in×高in ÷ 139 → lb */
    private static final double DIM_DIVISOR_INCH = 139;
    /** 退货时佣金退还但扣留 20% */
    private static final double REFUND_COMMISSION_KEEP = 0.2;
    private static final double DEFAULT_VOLUME_DIVISOR = 5000;
    private static final double DEFAULT_TARGET_MARGIN = 0.20;
    /** 价格带分界 */
    private static final double PRICE_BAND_LOW = 10;
    private static final double PRICE_BAND_MID = 50;
    /** 大号标准超过 48oz（3lb）后按每 4oz 步进 */
    private static final double LARGE_STANDARD_OZ_LIMIT = 48;
    private static final double STEP_OZ = 4;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ProfitDictLoader dict;
    private final SizeTierService sizeTierService;
    private final DictFirstLegMethodMapper firstLegMapper;
    private final ProductMapper productMapper;
    private final ProductFbaFeeMapper fbaFeeMapper;
    private final ProfitPlanMapper planMapper;
    private final ProfitCalcResultMapper resultMapper;

    /* ================================================================ */
    /* 计算（纯函数，不落库）                                             */
    /* ================================================================ */

    public ProfitCalcResult calculate(ProfitCalcFormDTO f) {
        double price = d(f.getSalePriceUsd());
        double rate = d(f.getExchangeRate());

        /* 1. 尺寸规范化：先换算再保留 2 位小数（与原表口径一致） */
        boolean inch = "inch".equalsIgnoreCase(f.getSizeUnit());
        double cmFactor = inch ? INCH_TO_CM : 1;
        double inchFactor = inch ? 1 : CM_TO_INCH;
        double lCm = r2(d(f.getLength()) * cmFactor);
        double wCm = r2(d(f.getWidth()) * cmFactor);
        double hCm = r2(d(f.getHeight()) * cmFactor);
        double lIn = r2(d(f.getLength()) * inchFactor);
        double wIn = r2(d(f.getWidth()) * inchFactor);
        double hIn = r2(d(f.getHeight()) * inchFactor);

        /* 2. 抛货体积重与实重 */
        double divisor = f.getVolumeDivisor() == null ? DEFAULT_VOLUME_DIVISOR : f.getVolumeDivisor();
        double volWeightKg = divisor > 0 ? lCm * wCm * hCm / divisor : 0;
        double volWeightLb = volWeightKg * KG_TO_LB;
        double weightKg = d(f.getWeightKg());                 // 含包装实重
        double weightLb = weightKg * KG_TO_LB;

        /* 3. 头程运费：按配送方式分叉（与 product 表口径一致）
           AMAZON_SEND：单件 = 当前产品配送总费用($) × 汇率 ÷ 当前产品件数；件数缺失按 0 处理。
           费用与件数都从「整票」改成「当前产品」，分子分母同步缩小，所以公式本身不用改
           SELF_CARRIER（含空值，历史口径）：MAX(实重, 抛重) × 单价 */
        boolean amazonSend = "AMAZON_SEND".equalsIgnoreCase(f.getShippingMethod());
        double firstLeg;
        if (amazonSend) {
            int units = f.getTotalUnits() == null ? 0 : f.getTotalUnits();
            firstLeg = units > 0 ? d(f.getShippingTotalFeeUsd()) * rate / units : 0;
        } else {
            firstLeg = Math.max(weightKg, volWeightKg) * d(f.getFirstLegPrice());
        }

        /* 4. 尺寸分段（判定用实重）+ 计费重（体积重 lb 参与取大） */
        String tierCode = sizeTierService.resolve(bd(lCm), bd(wCm), bd(hCm), bd(weightKg * 1000));
        DictFbaSizeTier tier = sizeTierService.tierOf(tierCode);
        double dimLb = lIn * wIn * hIn / DIM_DIVISOR_INCH;
        double billLb = Math.max(weightLb, dimLb);
        String billBasis = dimLb > weightLb ? "dimensional" : "actual";

        /* 5. 尾程配送费（已含燃油附加） */
        Fba fba = fbaFee(tier, weightLb, billLb, price);

        /* 6. 类目佣金（支持 FLAT / FULL 跳档 / MARGINAL 累进） */
        Commission commission = commission(f.getCommissionCategory(), price,
                Boolean.TRUE.equals(f.getUseMinCommission()));

        /* 7. 退货相关 */
        double threshold = dict.returnThreshold(f.getReturnCategory()).doubleValue();
        double retRate = pct(f.getReturnRate());
        double diff = Math.max(0, retRate - threshold);
        double retFee = d(f.getReturnFeeUsd());
        // 平均退货费用
        double retCost = ((commission.amount * REFUND_COMMISSION_KEEP + fba.fee) * retRate
                + diff * retFee) * rate;

        /* 8. 汇总 —— 两笔新费用（入库配置费 / 月度仓储费）与佣金同口径进括号 × 汇率 */
        double storage = d(f.getStorageFeeUsd());
        double inbound = d(f.getInboundConfigFeeUsd());       // null → 0
        double monthly = d(f.getMonthlyStorageUsd());         // null → 0
        double purchase = d(f.getPurchaseCost());

        // 总售价 人民币
        double revenue = price * rate;
        // ((广告费率 + VAT费率 + 其他杂费费率) * 售价($) + 佣金费用 + 尾程配送费(含燃油) + 合仓费
        // + 入库配置费 + 仓储管理费) * 汇率 + 产品成本 + 平均退货费 + 头程物流 = 产品总成本(￥)
        double totalCost = ((pct(f.getAdRate()) + pct(f.getVatRate()) + pct(f.getMiscRate())) * price
                + commission.amount + fba.fee + storage + inbound + monthly) * rate
                + purchase + retCost + firstLeg;
        // 前期垫资成本(￥) = 产品总成本 + 头程运费 + 类目佣金
        double advance = purchase + firstLeg + commission.amount * rate;
        // FBA总费用
        double fbaTotal = (fba.fee + storage + retFee + inbound + monthly) * rate + retCost;
        // 总利润
        double profit = revenue - totalCost;
        double margin = revenue == 0 ? 0 : profit / revenue;
        double targetMargin = f.getTargetMargin() == null ? DEFAULT_TARGET_MARGIN : pct(f.getTargetMargin());

        /* 9. 组装结果快照（字段与 calc.js 返回值逐列对应） */
        ProfitCalcResult r = new ProfitCalcResult();
        r.setDictVersion(commission.dictVersion());
        r.setSizeCmL(bd(lCm));
        r.setSizeCmW(bd(wCm));
        r.setSizeCmH(bd(hCm));
        r.setSizeInchL(bd(lIn));
        r.setSizeInchW(bd(wIn));
        r.setSizeInchH(bd(hIn));
        r.setVolWeightKg(bd(volWeightKg));
        r.setVolWeightLb(bd(volWeightLb));
        r.setWeightLb(bd(weightLb));
        r.setDimWeightLb(bd(dimLb));
        r.setBillWeightLb(bd(billLb));
        r.setBillWeightBasis(billBasis);
        r.setFirstLegCostCny(bd(firstLeg));
        r.setSizeTierKey(tierCode);
        r.setSizeTierLabel(tier == null ? null : tier.getTierName());
        r.setPriceBand(fba.band());
        r.setPriceBandLabel(fba.bandLabel());
        r.setFbaFeeBeforeFuelUsd(bd(fba.beforeFuel()));
        r.setFbaFeeUsd(bd(fba.fee()));
        r.setCommissionCategoryCode(commission.categoryCode());
        r.setCommissionRate(bd(commission.rate()));
        r.setCommissionAmountUsd(bd(commission.amount()));
        r.setReturnThreshold(bd(threshold));
        r.setReturnRate(bd(retRate));
        r.setThresholdDiff(bd(diff));
        r.setReturnCostCny(bd(retCost));
        r.setPurchaseCost(bd(purchase));
        r.setStorageFeeUsd(bd(storage));
        r.setInboundConfigFeeUsd(bd(inbound));
        r.setMonthlyStorageUsd(bd(monthly));
        r.setReturnFeeUsd(bd(retFee));
        r.setAdRate(bd(pct(f.getAdRate())));
        r.setVatRate(bd(pct(f.getVatRate())));
        r.setMiscRate(bd(pct(f.getMiscRate())));
        r.setExchangeRate(bd(rate));
        r.setSalePriceUsd(bd(price));
        r.setRevenueCny(bd(revenue));
        r.setTotalCostCny(bd(totalCost));
        r.setAdvanceCostCny(bd(advance));
        r.setFbaTotalCny(bd(fbaTotal));
        r.setProfitCny(bd(profit));
        r.setMarginRate(bd(margin));
        r.setTargetMargin(bd(targetMargin));
        r.setMarginStatus(profit <= 0 ? "danger" : margin >= targetMargin ? "good" : "warn");
        return r;
    }

    /* ================================================================ */
    /* 保存（事务：方案 + 结果快照 + 按需 upsert 产品费用）                */
    /* ================================================================ */

    @Transactional
    public ProfitCalcResult save(ProfitCalcFormDTO f) {
        ProfitCalcResult r = calculate(f);

        ProfitPlan plan = new ProfitPlan();
        BeanUtils.copyProperties(f, plan);     // 同名同类型字段直接拷（含两笔新费用）
        plan.setId(null);
        plan.setPlanNo(nextPlanNo());
        plan.setSource(f.getProductId() == null ? "MANUAL" : "PRODUCT");
        plan.setUseMinCommission(Boolean.TRUE.equals(f.getUseMinCommission()) ? 1 : 0);
        planMapper.insert(plan);

        r.setPlanId(plan.getId());
        r.setProductId(f.getProductId());
        r.setCalcAt(LocalDateTime.now(ZONE));
        resultMapper.insert(r);

        // 没选产品 → 纯手填方案，product_fba_fee 完全不动
        if (f.getProductId() != null) {
            upsertFbaFee(f, r);
        }
        return r;
    }

    /* ================================================================ */
    /* 产品带出（计算器顶部下拉选中后回填表单）                            */
    /* ================================================================ */

    public ProfitCalcFormDTO prefill(Long productId) {
        Product p = productMapper.selectOne(
                new LambdaQueryWrapper<Product>().eq(Product::getId, productId));
        if (p == null) {
            throw new CYXYException("产品不存在或已经删除");
        }

        ProfitCalcFormDTO f = new ProfitCalcFormDTO();
        f.setProductId(productId);
        f.setSizeUnit("cm");
        f.setLength(p.getLengthCm());
        f.setWidth(p.getWidthCm());
        f.setHeight(p.getHeightCm());
        // 定案：带出「含包装实重」，字段名仍叫 weight_kg
        f.setWeightKg(p.getPackagedWeightG() == null ? null
                : p.getPackagedWeightG().divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP));
        // 采购成本 = 采购单价 + 包材贴标 + 平均国内运费（与 profit_plan.purchaseCost 口径注释一致）
        f.setPurchaseCost(nz(p.getPurchasePrice()).add(nz(p.getPackageCost())).add(nz(p.getAvgDomesticShipping())));
        f.setSalePriceUsd(p.getListPriceUsd());
        f.setLogisticsProvider(p.getLogisticsProvider());

        // 配送方式：决定头程算法；历史空值归一成 SELF_CARRIER，前端下拉能直接回显
        f.setShippingMethod(p.getShippingMethod() == null ? "SELF_CARRIER" : p.getShippingMethod());
        f.setFirstLegMethodCode(p.getFirstLegMethodCode());
        // SEND 费用两项：当前产品的配送费 $ + 当前产品的件数，页面只读展示并参与单件头程计算
        f.setShippingTotalFeeUsd(p.getShippingTotalFeeUsd());
        f.setTotalUnits(p.getTotalUnits());

        // 退货阈值类目：产品档案挂的就是这套字典，带出名称（下拉显示用）
        if (p.getCategoryCode() != null) {
            DictCategoryReturnThreshold t = dict.threshold(p.getCategoryCode());
            if (t != null) {
                f.setReturnCategory(t.getCategoryName());
            }
        }
        // 佣金类目：两套字典不联动，故意不带出，由用户每轮手选

        // 运费单价：产品档案里录过的单价优先（它才是「产品已有的信息」），
        // 没录再按头程方式带默认单价；材积比始终由头程方式带出
        if (p.getFirstLegUnitPrice() != null) {
            f.setFirstLegPrice(p.getFirstLegUnitPrice());
        }
        if (p.getFirstLegMethodCode() != null) {
            DictFirstLegMethod m = firstLegMapper.selectById(p.getFirstLegMethodCode());
            if (m != null) {
                if (m.getVolumeDivisor() != null) {
                    f.setVolumeDivisor(m.getVolumeDivisor());
                }
                if (f.getFirstLegPrice() == null && m.getDefaultUnitPrice() != null) {
                    f.setFirstLegPrice(m.getDefaultUnitPrice());
                }
            }
        }

        // 汇率：最新一条，取不到给默认
        f.setExchangeRate(dict.latestUsdCnyRate());

        // 上次测算的手填费用带回，避免重复录入；没测算过则留空
        ProductFbaFee existing = fbaFeeMapper.selectOne(
                new LambdaQueryWrapper<ProductFbaFee>().eq(ProductFbaFee::getProductId, productId));
        if (existing != null) {
            f.setInboundConfigFeeUsd(existing.getInboundConfigFeeUsd());
            f.setMonthlyStorageUsd(existing.getMonthlyStorageUsd());
            f.setStorageFeeUsd(existing.getStorageFeeUsd());
            if (f.getSalePriceUsd() == null) {
                f.setSalePriceUsd(existing.getBasedPriceUsd());
            }
        }
        return f;
    }

    /* ================================================================ */
    /* 内部实现                                                           */
    /* ================================================================ */

    /** 尾程配送费：查费率取实重还是计费重，由字典 weight_basis 决定（small=ACTUAL，其余=BILLABLE） */
    private Fba fbaFee(DictFbaSizeTier tier, double weightLb, double billLb, double price) {
        if (tier == null) {
            return new Fba(null, null, 0, 0);
        }
        String band = price < PRICE_BAND_LOW ? "low" : price <= PRICE_BAND_MID ? "mid" : "high";
        String bandKey = band.toUpperCase();
        String bandLabel = switch (band) {
            case "low" -> "< $10";
            case "mid" -> "$10 ~ $50";
            default -> "> $50";
        };

        double lookupLb = "ACTUAL".equalsIgnoreCase(tier.getWeightBasis()) ? weightLb : billLb;

        double beforeFuel = 0;
        switch (tier.getTierCode()) {
            case "small" -> beforeFuel = rateOf(dict.fbaRate("small", bandKey, lookupLb * OZ_PER_LB));
            case "large" -> {
                double totalOz = lookupLb * OZ_PER_LB;
                if (totalOz <= LARGE_STANDARD_OZ_LIMIT) {
                    beforeFuel = rateOf(dict.fbaRate("large", bandKey, totalOz));
                } else {
                    // 超过 48oz：阶梯步进，每 4oz 一跳
                    DictFbaStepRate step = dict.stepRate("large", bandKey, "STEP_4OZ");
                    if (step != null) {
                        beforeFuel = d(step.getBaseUsd()) + d(step.getStepUsd())
                                * Math.ceil((totalOz - LARGE_STANDARD_OZ_LIMIT) / STEP_OZ);
                    }
                }
            }
            default -> {
                // 大件：首磅 + 续磅，按计费重
                DictFbaStepRate over = dict.stepRate("oversize", bandKey, "FIRST_POUND_PLUS");
                if (over != null) {
                    beforeFuel = d(over.getBaseUsd()) + d(over.getStepUsd())
                            * Math.max(0, Math.ceil(billLb - 1));
                }
            }
        }
        double fee = beforeFuel * (1 + FUEL_SURCHARGE);
        return new Fba(band, bandLabel, beforeFuel, fee);
    }

    /** 佣金：FLAT 单一费率 / FULL 全额跳档 / MARGINAL 分段累进，支持最低佣金兜底 */
    private Commission commission(String nameOrCode, double price, boolean useMin) {
        DictCategoryCommission cat = dict.commission(nameOrCode);
        if (cat == null) {
            return new Commission(null, null, 0, 0);
        }
        List<DictCategoryCommissionTier> tiers = dict.tiers(cat.getCategoryCode());
        String mode = cat.getFeeMode() == null ? "FLAT" : cat.getFeeMode();

        double amount = 0;
        double rate = 0;
        if ("FLAT".equals(mode) || tiers.isEmpty()) {
            rate = d(cat.getFlatRate());
            amount = price > 0 ? price * rate : 0;
        } else if ("FULL".equals(mode)) {
            // 全额跳档：总价落哪一档，整套按该档费率
            DictCategoryCommissionTier hit = null;
            for (DictCategoryCommissionTier t : tiers) {
                if (t.getPriceMaxUsd() == null || price <= t.getPriceMaxUsd().doubleValue()) {
                    hit = t;
                    break;
                }
            }
            if (hit == null) {
                hit = tiers.get(tiers.size() - 1);
            }
            amount = price * d(hit.getTierRate());
        } else {
            // 分段累进：各区间分别计算后相加
            double prev = 0;
            for (DictCategoryCommissionTier t : tiers) {
                double upper = t.getPriceMaxUsd() == null
                        ? Double.POSITIVE_INFINITY : t.getPriceMaxUsd().doubleValue();
                if (price <= prev) {
                    break;
                }
                amount += (Math.min(price, upper) - prev) * d(t.getTierRate());
                prev = upper;
            }
        }
        if (useMin && cat.getMinCommissionUsd() != null) {
            amount = Math.max(amount, cat.getMinCommissionUsd().doubleValue());
        }
        // 与前端一致：非 FLAT 模式费率 = 最终金额 ÷ 售价
        if (!"FLAT".equals(mode) && price > 0) {
            rate = amount / price;
        }
        String dictVersion = cat.getValidFrom() == null ? null : cat.getValidFrom().toString();
        return new Commission(cat.getCategoryCode(), dictVersion, amount, rate);
    }

    /** 保存测算时整行 upsert 产品 FBA 费用（首次新增 / 已有则更新，软删一并复位） */
    private void upsertFbaFee(ProfitCalcFormDTO f, ProfitCalcResult r) {
        ProductFbaFee fee = new ProductFbaFee();
        fee.setProductId(f.getProductId());
        fee.setInboundConfigFeeUsd(r.getInboundConfigFeeUsd());
        fee.setMonthlyStorageUsd(r.getMonthlyStorageUsd());
        fee.setFbaFeeUsd(r.getFbaFeeUsd());
        fee.setStorageFeeUsd(r.getStorageFeeUsd());
        fee.setCommissionUsd(r.getCommissionAmountUsd());
        fee.setAvgReturnCostCny(r.getReturnCostCny());
        fee.setFbaTotalCny(r.getFbaTotalCny());
        fee.setBasedPriceUsd(r.getSalePriceUsd());
        fee.setCalcAt(r.getCalcAt());
        fbaFeeMapper.upsert(fee);
    }

    /** 方案编号：PLAN-年月日时分秒-4 位随机，可读且基本不重 */
    private String nextPlanNo() {
        String ts = LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "PLAN-" + ts + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    /* ---------------- 数值工具：null 安全 + 前端口径 ---------------- */

    private static double d(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    /** 百分制转比率：15 → 0.15 */
    private static double pct(BigDecimal v) {
        return d(v) / 100.0;
    }

    /** 与前端 round(x, 2) 对齐 */
    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static double rateOf(DictFbaRate row) {
        return row == null || row.getRateUsd() == null ? 0 : row.getRateUsd().doubleValue();
    }

    /* ---------------- 中间结果载体（record，避免数组下标魔法） ---------------- */

    private record Fba(String band, String bandLabel, double beforeFuel, double fee) {
    }

    private record Commission(String categoryCode, String dictVersion, double amount, double rate) {
    }
}
