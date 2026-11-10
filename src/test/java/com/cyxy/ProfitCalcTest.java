package com.cyxy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.mapper.ProductFbaFeeMapper;
import com.cyxy.mapper.ProductMapper;
import com.cyxy.mapper.ProfitPlanMapper;
import com.cyxy.mapper.SysConfigMapper;
import com.cyxy.model.dto.ProfitCalcFormDTO;
import com.cyxy.model.entity.SysConfig;
import com.cyxy.model.entity.Product;
import com.cyxy.model.entity.ProductFbaFee;
import com.cyxy.model.entity.ProfitPlan;
import com.cyxy.service.ProfitCalcService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 利润计算器测试。
 *
 * 回归基准来自前端 calc.js（与原 Excel 000002 主表一致）：
 * 24×22.5×2cm / 含包装实重 0.22kg / 售价 $18.99 / 汇率 6.68 / 宠物用品（FLAT 15%、阈值 10.2%）
 * → 总成本 ￥95.13 / 利润 ￥31.72 / 利润率 25.0071%。
 *
 * 全程 @Transactional，测试结束回滚，不在库里留数据。
 */
@SpringBootTest
@Transactional
class ProfitCalcTest {

    @Autowired
    private ProfitCalcService profitCalcService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductFbaFeeMapper fbaFeeMapper;

    @Autowired
    private ProfitPlanMapper planMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    /* ------------------------------------------------------------------ */
    /* 计算                                                                */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("回归基准：与前端 calc.js 逐位对齐")
    void regressionBaseline() {
        ProfitCalcResultHolder r = calc(baseForm());

        // 结构性中间值
        assertEquals("large", r.result.getSizeTierKey(), "0.79in 最短边超小号 0.75 上限 → 大号标准");
        assertEquals("mid", r.result.getPriceBand());
        assertEquals("actual", r.result.getBillWeightBasis());
        assertEquals(0, r.result.getCommissionRate().compareTo(new BigDecimal("0.15")), "宠物用品 FLAT 15%");
        assertEquals(2.8485, r.result.getCommissionAmountUsd().doubleValue(), 0.0001, "18.99 × 15%");
        assertEquals(0, r.result.getFbaFeeBeforeFuelUsd().compareTo(new BigDecimal("4.21")),
                "7.76oz 落大号 MID 第 2 档");
        assertEquals(0, r.result.getReturnThreshold().compareTo(new BigDecimal("0.102")), "宠物用品阈值 10.2%");

        // 三大核心结果
        assertEquals(95.13, r.result.getTotalCostCny().doubleValue(), 0.01, "总成本");
        assertEquals(31.72, r.result.getProfitCny().doubleValue(), 0.01, "利润");
        assertEquals(0.250071, r.result.getMarginRate().doubleValue(), 0.0001, "利润率");
        assertEquals("good", r.result.getMarginStatus(), "25% ≥ 目标 15%");
    }

    @Test
    @DisplayName("两笔新费用参与计算：各填 $1 总成本增加 2 × 汇率")
    void newFeesJoinCalculation() {
        ProfitCalcResultHolder base = calc(baseForm());

        ProfitCalcFormDTO f = baseForm();
        f.setInboundConfigFeeUsd(BigDecimal.ONE);
        f.setMonthlyStorageUsd(BigDecimal.ONE);
        ProfitCalcResultHolder with = calc(f);

        assertEquals(base.result.getTotalCostCny().doubleValue() + 2 * 6.68,
                with.result.getTotalCostCny().doubleValue(), 0.001, "总成本增加 13.36");
        assertEquals(base.result.getFbaTotalCny().doubleValue() + 2 * 6.68,
                with.result.getFbaTotalCny().doubleValue(), 0.001, "FBA 合计同口径增加");
        assertEquals(base.result.getProfitCny().doubleValue() - 2 * 6.68,
                with.result.getProfitCny().doubleValue(), 0.001, "利润相应减少");
    }

    @Test
    @DisplayName("亚马逊SEND：单件头程 = 当前产品配送总费用 × 汇率 ÷ 当前产品件数")
    void firstLegAmazonSend() {
        ProfitCalcResultHolder base = calc(baseForm());

        ProfitCalcFormDTO f = baseForm();
        f.setShippingMethod("AMAZON_SEND");
        f.setShippingTotalFeeUsd(new BigDecimal("133.60"));
        f.setTotalUnits(20);
        ProfitCalcResultHolder r = calc(f);

        // 133.60 × 6.68 ÷ 20 = 44.6224
        assertEquals(44.6224, r.result.getFirstLegCostCny().doubleValue(), 0.0001, "单件头程");
        // 基准 SELF_CARRIER 头程 = MAX(0.22, 0.18) × 46 = 10.12，总成本差 = 新旧头程之差
        assertEquals(10.12, base.result.getFirstLegCostCny().doubleValue(), 0.0001, "基准单件头程");
        assertEquals(base.result.getTotalCostCny().doubleValue() + (44.6224 - 10.12),
                r.result.getTotalCostCny().doubleValue(), 0.001, "总成本按头程差额平移");
    }

    @Test
    @DisplayName("亚马逊SEND：当前产品件数为空/0 时头程按 0 算，不抛除零")
    void firstLegAmazonSendNoUnits() {
        ProfitCalcFormDTO f = baseForm();
        f.setShippingMethod("AMAZON_SEND");
        f.setShippingTotalFeeUsd(new BigDecimal("133.60"));
        f.setTotalUnits(null);
        assertEquals(0, profitCalcService.calculate(f).getFirstLegCostCny().compareTo(BigDecimal.ZERO));

        f.setTotalUnits(0);
        assertEquals(0, profitCalcService.calculate(f).getFirstLegCostCny().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("配送方式为空（历史方案）按自选承运人算，与基准一致")
    void shippingMethodNullFallsBack() {
        ProfitCalcFormDTO f = baseForm();
        f.setShippingMethod(null);
        f.setShippingTotalFeeUsd(new BigDecimal("133.60"));  // 空模式应忽略这两个值
        f.setTotalUnits(20);
        ProfitCalcResultHolder r = calc(f);
        assertEquals(10.12, r.result.getFirstLegCostCny().doubleValue(), 0.0001,
                "空配送方式忽略总费用/件数，仍按单价 × 计费重");
        assertEquals(95.13, r.result.getTotalCostCny().doubleValue(), 0.01);
    }

    @Test
    @DisplayName("新费用为空时按 0 处理，结果与基准一致")
    void newFeesNullAsZero() {
        ProfitCalcFormDTO f = baseForm();
        f.setInboundConfigFeeUsd(null);
        f.setMonthlyStorageUsd(null);
        ProfitCalcResultHolder r = calc(f);
        assertEquals(95.13, r.result.getTotalCostCny().doubleValue(), 0.01);
        assertEquals(0, r.result.getInboundConfigFeeUsd().compareTo(BigDecimal.ZERO));
        assertEquals(0, r.result.getMonthlyStorageUsd().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("FULL 跳档佣金（美妆）：$15 落第二档按 15% 整套计")
    void commissionFullMode() {
        ProfitCalcFormDTO f = baseForm();
        f.setCommissionCategory("美妆");
        f.setSalePriceUsd(new BigDecimal("15"));
        ProfitCalcResultHolder r = calc(f);
        assertEquals(0, r.result.getCommissionAmountUsd().compareTo(new BigDecimal("2.25")),
                "美妆 >$10 档费率 15%");
        assertEquals(0, r.result.getCommissionRate().compareTo(new BigDecimal("0.15")));
    }

    @Test
    @DisplayName("MARGINAL 累进佣金（电子产品配件）：$150 = 100×15% + 50×8%")
    void commissionMarginalMode() {
        ProfitCalcFormDTO f = baseForm();
        f.setCommissionCategory("电子产品配件");
        f.setSalePriceUsd(new BigDecimal("150"));
        ProfitCalcResultHolder r = calc(f);
        assertEquals(0, r.result.getCommissionAmountUsd().compareTo(new BigDecimal("19")),
                "100×0.15 + 50×0.08 = 19");
        assertEquals(0.126667, r.result.getCommissionRate().doubleValue(), 0.0001, "费率 = 金额 ÷ 售价");
    }

    @Test
    @DisplayName("最低佣金兜底：$1 宠物用品佣金 0.15，取最低 $0.30")
    void commissionMinFloor() {
        ProfitCalcFormDTO f = baseForm();
        f.setSalePriceUsd(BigDecimal.ONE);
        f.setUseMinCommission(true);
        ProfitCalcResultHolder r = calc(f);
        assertEquals(0, r.result.getCommissionAmountUsd().compareTo(new BigDecimal("0.30")));
    }

    @Test
    @DisplayName("大号标准超 48oz 走 4oz 阶梯步进")
    void largeStandardStepRate() {
        ProfitCalcFormDTO f = baseForm();
        f.setWeightKg(new BigDecimal("2.5"));        // 2.5kg ≈ 5.51lb ≈ 88.2oz > 48oz
        ProfitCalcResultHolder r = calc(f);
        // base 7.51 + 0.08 × ceil((88.19-48)/4) = 7.51 + 0.08×11 = 8.39（MID 档，实重计费）
        assertEquals(0, r.result.getFbaFeeBeforeFuelUsd().compareTo(new BigDecimal("8.39")));
    }

    @Test
    @DisplayName("利润为负 → danger；低于目标 → warn")
    void marginStatus() {
        ProfitCalcFormDTO f = baseForm();
        f.setSalePriceUsd(new BigDecimal("5"));      // 低价必然亏损
        assertEquals("danger", profitCalcService.calculate(f).getMarginStatus());

        f = baseForm();
        f.setTargetMargin(new BigDecimal("40"));     // 25% < 40%
        assertEquals("warn", profitCalcService.calculate(f).getMarginStatus());
    }

    /* ------------------------------------------------------------------ */
    /* 保存分流                                                            */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("保存（无产品）：方案 + 结果落库，product_fba_fee 一行不多")
    void saveWithoutProduct() {
        long before = fbaFeeMapper.selectCount(null);

        ProfitCalcFormDTO f = baseForm();
        f.setPlanName("纯手填方案");
        var r = profitCalcService.save(f);

        assertNotNull(r.getPlanId());
        assertNull(r.getProductId());

        ProfitPlan plan = planMapper.selectById(r.getPlanId());
        assertNotNull(plan);
        assertTrue(plan.getPlanNo().startsWith("PLAN-"), "方案编号格式 PLAN-...");
        assertEquals("MANUAL", plan.getSource());
        assertNull(plan.getProductId());
        assertEquals(0, plan.getUseMinCommission());
        assertTrue(plan.getAdRate().compareTo(new BigDecimal("15")) == 0, "plan 存百分制 15");
        assertTrue(plan.getInboundConfigFeeUsd().compareTo(BigDecimal.ZERO) == 0, "两笔新费用进方案快照");

        assertEquals(before, fbaFeeMapper.selectCount(null), "没选产品不动 FBA 费用表");
    }

    @Test
    @DisplayName("保存（选产品）：首次 upsert 新增，再次保存更新同一行")
    void saveWithProductUpsert() {
        Product p = insertProduct();

        ProfitCalcFormDTO f = baseForm();
        f.setProductId(p.getId());
        f.setPlanName("产品方案");
        f.setInboundConfigFeeUsd(new BigDecimal("0.5"));
        f.setMonthlyStorageUsd(new BigDecimal("0.3"));
        var r1 = profitCalcService.save(f);

        assertEquals(p.getId(), r1.getProductId());
        ProductFbaFee fee = feeOf(p.getId());
        assertNotNull(fee, "首次保存应新增费用行");
        assertEquals(r1.getFbaFeeUsd().doubleValue(), fee.getFbaFeeUsd().doubleValue(), 0.000001);
        assertEquals(r1.getCommissionAmountUsd().doubleValue(), fee.getCommissionUsd().doubleValue(), 0.000001);
        assertTrue(fee.getBasedPriceUsd().compareTo(new BigDecimal("18.99")) == 0);
        assertTrue(fee.getInboundConfigFeeUsd().compareTo(new BigDecimal("0.5")) == 0, "手填费用进表");
        assertNotNull(fee.getCalcAt());

        // 换售价再存一次：仍是同一行，费用按新售价刷新
        f.setSalePriceUsd(new BigDecimal("25"));
        var r2 = profitCalcService.save(f);

        assertEquals(1L, fbaFeeMapper.selectCount(
                        new LambdaQueryWrapper<ProductFbaFee>().eq(ProductFbaFee::getProductId, p.getId())),
                "一个产品只允许一行");
        ProductFbaFee fee2 = feeOf(p.getId());
        assertTrue(fee2.getBasedPriceUsd().compareTo(new BigDecimal("25")) == 0, "基于售价刷新");
        assertEquals(r2.getFbaFeeUsd().doubleValue(), fee2.getFbaFeeUsd().doubleValue(), 0.000001);

        // 方案与结果是两条独立历史
        assertEquals(2, planMapper.selectCount(
                new LambdaQueryWrapper<ProfitPlan>().eq(ProfitPlan::getProductId, p.getId())));
    }

    @Test
    @DisplayName("upsert 软删复位：软删后再保存，行恢复可见且仍只有一行")
    void upsertResurrectsSoftDeletedRow() {
        Product p = insertProduct();

        ProfitCalcFormDTO f = baseForm();
        f.setProductId(p.getId());
        profitCalcService.save(f);

        Long feeId = feeOf(p.getId()).getId();
        fbaFeeMapper.deleteById(feeId);           // @TableLogic → UPDATE is_deleted=1
        assertNull(feeOf(p.getId()), "软删后 MP 查询应不可见");

        profitCalcService.save(f);                // 原生 upsert，is_deleted 复位 0
        ProductFbaFee revived = feeOf(p.getId());
        assertNotNull(revived, "upsert 后软删行应复活");
        assertEquals(1L, fbaFeeMapper.selectCount(
                new LambdaQueryWrapper<ProductFbaFee>().eq(ProductFbaFee::getProductId, p.getId())));
    }

    /* ------------------------------------------------------------------ */
    /* 产品带出                                                            */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("prefill：带出尺寸/含包装实重/采购成本/退货类目/头程/汇率")
    void prefillFromProduct() {
        Product p = insertProduct();
        ProfitCalcFormDTO f = profitCalcService.prefill(p.getId());

        assertEquals(p.getId(), f.getProductId());
        assertEquals("cm", f.getSizeUnit());
        assertTrue(f.getLength().compareTo(new BigDecimal("24")) == 0);
        // 定案口径：含包装实重 = packaged_weight_g ÷ 1000
        assertTrue(f.getWeightKg().compareTo(new BigDecimal("0.22")) == 0);
        // 采购成本 = 采购单价 + 包材贴标 + 平均国内运费 = 13.5 + 0.5 + 1.2
        assertTrue(f.getPurchaseCost().compareTo(new BigDecimal("15.2")) == 0);
        assertTrue(f.getSalePriceUsd().compareTo(new BigDecimal("18.99")) == 0);
        // 退货阈值类目带名称，佣金类目故意不带
        assertEquals("宠物用品", f.getReturnCategory());
        assertNull(f.getCommissionCategory());
        // 头程走 SEA：默认单价 8.00，材积比 5000
        assertTrue(f.getFirstLegPrice().compareTo(new BigDecimal("8.00")) == 0);
        assertEquals(5000, f.getVolumeDivisor());
        // prefill 的汇率来自全局参数「最新汇率」，不能写死 6.68：
        // 一旦汇率同步过，这里就该返回同步后的当前值。回归基准另在 baseForm() 里固定 6.68，
        // 所以实时汇率怎么变都不会把 ￥95.13 / ￥31.72 的基准算崩。
        SysConfig cfg = sysConfigMapper.selectById("DEFAULT_EXCHANGE_RATE");
        assertEquals(0, new BigDecimal(cfg.getConfigValue()).compareTo(f.getExchangeRate()),
                "prefill 汇率应等于全局参数「最新汇率」");
        assertNull(f.getInboundConfigFeeUsd(), "没测算过，手填费用留空");
    }

    @Test
    @DisplayName("prefill：测算过一次后，手填费用与合仓费带回")
    void prefillAfterSaved() {
        Product p = insertProduct();

        ProfitCalcFormDTO f = baseForm();
        f.setProductId(p.getId());
        f.setStorageFeeUsd(new BigDecimal("0.8"));
        profitCalcService.save(f);

        ProfitCalcFormDTO again = profitCalcService.prefill(p.getId());
        assertTrue(again.getStorageFeeUsd().compareTo(new BigDecimal("0.8")) == 0, "合仓费带回");
    }

    @Test
    @DisplayName("prefill：配送方式/当前产品费用/件数带出，产品自带单价优先于头程方式默认价")
    void prefillShippingMethod() {
        Product p = insertProduct();
        p.setShippingMethod("AMAZON_SEND");
        p.setShippingTotalFeeUsd(new BigDecimal("133.60"));
        p.setTotalUnits(20);
        p.setFirstLegUnitPrice(new BigDecimal("9.9"));   // 产品自带单价，应压过 SEA 默认 8
        productMapper.updateById(p);

        ProfitCalcFormDTO f = profitCalcService.prefill(p.getId());
        assertEquals("AMAZON_SEND", f.getShippingMethod());
        assertTrue(f.getShippingTotalFeeUsd().compareTo(new BigDecimal("133.60")) == 0, "当前产品费用带出");
        assertEquals(20, f.getTotalUnits());
        assertEquals("SEA", f.getFirstLegMethodCode(), "头程方式带出");
        assertTrue(f.getFirstLegPrice().compareTo(new BigDecimal("9.9")) == 0, "产品自带单价优先");
        assertEquals(5000, f.getVolumeDivisor(), "材积比仍由头程方式带出");
    }

    @Test
    @DisplayName("prefill：配送方式为空（历史产品）归一成 SELF_CARRIER")
    void prefillShippingMethodNullNormalized() {
        Product p = insertProduct();                     // insertProduct 不设 shippingMethod
        ProfitCalcFormDTO f = profitCalcService.prefill(p.getId());
        assertEquals("SELF_CARRIER", f.getShippingMethod(), "空值归一，前端下拉直接回显");
    }

    /* ------------------------------------------------------------------ */
    /* 工具                                                                */
    /* ------------------------------------------------------------------ */

    /** 与前端 createDefaultForm 的基准一致，仅尺寸改为回归用的 24×22.5×2 */
    private ProfitCalcFormDTO baseForm() {
        ProfitCalcFormDTO f = new ProfitCalcFormDTO();
        f.setSizeUnit("cm");
        f.setLength(new BigDecimal("24"));
        f.setWidth(new BigDecimal("22.5"));
        f.setHeight(new BigDecimal("2"));
        f.setWeightKg(new BigDecimal("0.22"));
        f.setPurchaseCost(new BigDecimal("15.22"));
        f.setAdRate(new BigDecimal("15"));
        f.setVatRate(BigDecimal.ZERO);
        f.setMiscRate(new BigDecimal("1"));
        f.setVolumeDivisor(6000);
        f.setFirstLegPrice(new BigDecimal("46"));
        f.setCommissionCategory("宠物用品");
        f.setReturnCategory("宠物用品");
        f.setReturnRate(new BigDecimal("4.13"));
        f.setReturnFeeUsd(new BigDecimal("3.39"));
        f.setStorageFeeUsd(BigDecimal.ZERO);
        f.setInboundConfigFeeUsd(BigDecimal.ZERO);
        f.setMonthlyStorageUsd(BigDecimal.ZERO);
        f.setSalePriceUsd(new BigDecimal("18.99"));
        f.setExchangeRate(new BigDecimal("6.68"));
        f.setTargetMargin(new BigDecimal("15"));
        f.setUseMinCommission(false);
        return f;
    }

    /** 造一个最小可用产品：基准尺寸 + 宠物类目 + 海运头程（带出断言依赖这些值） */
    private Product insertProduct() {
        Product p = new Product();
        p.setProductNo("PF-" + UUID.randomUUID().toString().substring(0, 8));
        p.setNameCn("利润计算测试品");
        p.setMainImage("https://example.com/a.png");
        p.setSourceUrl("https://detail.1688.com/offer/test.html");
        p.setPurchasePrice(new BigDecimal("13.5000"));
        p.setPackageCost(new BigDecimal("0.5"));
        p.setAvgDomesticShipping(new BigDecimal("1.2000"));
        p.setNetWeightG(new BigDecimal("205.00"));
        p.setPackagedWeightG(new BigDecimal("220.00"));
        p.setLengthCm(new BigDecimal("24.00"));
        p.setWidthCm(new BigDecimal("22.50"));
        p.setHeightCm(new BigDecimal("2.00"));
        p.setListPriceUsd(new BigDecimal("18.99"));
        p.setCategoryCode("pet_supplies");
        p.setFirstLegMethodCode("SEA");
        p.setLogisticsProvider("测试物流");
        p.setStatus("DRAFT");
        productMapper.insert(p);
        return p;
    }

    private ProductFbaFee feeOf(Long productId) {
        return fbaFeeMapper.selectOne(
                new LambdaQueryWrapper<ProductFbaFee>().eq(ProductFbaFee::getProductId, productId));
    }

    /** 只为让断言读起来带字段名的轻量壳 */
    private record ProfitCalcResultHolder(com.cyxy.model.entity.ProfitCalcResult result) {
    }

    private ProfitCalcResultHolder calc(ProfitCalcFormDTO f) {
        return new ProfitCalcResultHolder(profitCalcService.calculate(f));
    }
}
