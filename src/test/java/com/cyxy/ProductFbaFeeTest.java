package com.cyxy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.mapper.ProductFbaFeeMapper;
import com.cyxy.mapper.ProductMapper;
import com.cyxy.model.entity.Product;
import com.cyxy.model.entity.ProductFbaFee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * product_fba_fee 的映射与「分阶段部分更新」语义测试。
 *
 * 业务约定（务必保持）：
 *   阶段一 录入产品：只写 inbound_config_fee_usd / monthly_storage_usd，其余留空
 *   阶段二 利润测算：回填 fba_fee_usd / storage_fee_usd / commission_usd /
 *                    avg_return_cost_cny / fba_total_cny
 *   两个阶段互不覆盖 —— 阶段二只更新自己那批字段，不动阶段一已写入的值
 *
 * 全程 @Transactional，测试结束回滚，不落库。
 */
@SpringBootTest
@Transactional
class ProductFbaFeeTest {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductFbaFeeMapper feeMapper;

    /** 造一个最小可用的产品，编号随机，避免唯一索引冲突 */
    private Product insertProduct() {
        Product p = new Product();
        p.setProductNo("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        p.setNameCn("测试商品");
        p.setNameEn("Test Product");
        // 真实库以下几个字段为 NOT NULL，构造数据时必须给值
        p.setMainImage("https://example.com/a.png");
        p.setSourceUrl("https://detail.1688.com/offer/test.html");
        p.setPurchasePrice(new BigDecimal("13.5000"));
        p.setAvgDomesticShipping(new BigDecimal("1.2000"));
        p.setNetWeightG(new BigDecimal("205.00"));
        p.setPackagedWeightG(new BigDecimal("220.00"));
        p.setLengthCm(new BigDecimal("24.00"));
        p.setWidthCm(new BigDecimal("22.50"));
        p.setHeightCm(new BigDecimal("2.00"));
        p.setListPriceUsd(new BigDecimal("18.99"));
        p.setStatus("DRAFT");
        productMapper.insert(p);
        return p;
    }

    @Test
    @DisplayName("阶段一：录入产品只写入库配置费与仓储费，其余字段应为空")
    void testStageOnePartialInsert() {
        Product p = insertProduct();

        ProductFbaFee fee = new ProductFbaFee();
        fee.setProductId(p.getId());
        fee.setInboundConfigFeeUsd(new BigDecimal("0.320000"));
        fee.setMonthlyStorageUsd(new BigDecimal("0.094000"));
        // 其余依赖售价的字段刻意不设值
        assertEquals(1, feeMapper.insert(fee), "插入应影响 1 行");
        assertNotNull(fee.getId(), "自增主键应回填");

        ProductFbaFee saved = feeMapper.selectById(fee.getId());
        assertNotNull(saved, "按 id 应能查回");

        // 阶段一写入的两项应有值
        assertEquals(0, saved.getInboundConfigFeeUsd().compareTo(new BigDecimal("0.320000")), "入库配置费应精确保留 6 位小数");
        assertEquals(0, saved.getMonthlyStorageUsd().compareTo(new BigDecimal("0.094000")), "月度仓储费应精确保留 6 位小数");

        // 阶段二的字段此时应全部为空
        assertNull(saved.getFbaFeeUsd(), "未测算时尾程配送费应为空");
        assertNull(saved.getStorageFeeUsd(), "未测算时合仓费应为空");
        assertNull(saved.getCommissionUsd(), "未测算时佣金应为空");
        assertNull(saved.getAvgReturnCostCny(), "未测算时平均退货费用应为空");
        assertNull(saved.getFbaTotalCny(), "未测算时 FBA 总费用应为空");
        assertNull(saved.getBasedPriceUsd(), "未测算时口径售价应为空");
        assertNull(saved.getCalcAt(), "未测算时测算时间应为空");
    }

    @Test
    @DisplayName("阶段二：测算后回填费用字段，且不覆盖阶段一已写入的值")
    void testStageTwoPartialUpdate() {
        Product p = insertProduct();

        // --- 阶段一：录入 ---
        ProductFbaFee fee = new ProductFbaFee();
        fee.setProductId(p.getId());
        fee.setInboundConfigFeeUsd(new BigDecimal("0.320000"));
        fee.setMonthlyStorageUsd(new BigDecimal("0.094000"));
        feeMapper.insert(fee);

        // --- 阶段二：测算后回填（只 set 自己这批字段） ---
        ProductFbaFee patch = new ProductFbaFee();
        patch.setId(fee.getId());
        patch.setFbaFeeUsd(new BigDecimal("4.990000"));
        patch.setStorageFeeUsd(new BigDecimal("0.500000"));
        patch.setCommissionUsd(new BigDecimal("2.850000"));
        patch.setAvgReturnCostCny(new BigDecimal("1.0848"));
        patch.setFbaTotalCny(new BigDecimal("64.5001"));
        patch.setBasedPriceUsd(new BigDecimal("18.99"));
        patch.setCalcAt(LocalDateTime.now());
        // 注意：不 set inboundConfigFeeUsd / monthlyStorageUsd，验证它们不会被抹掉
        feeMapper.updateById(patch);

        // --- 校验 ---
        ProductFbaFee after = feeMapper.selectById(fee.getId());
        assertNotNull(after, "更新后应仍能查到");

        // 阶段二的字段已回填
        assertEquals(0, after.getFbaFeeUsd().compareTo(new BigDecimal("4.990000")), "尾程配送费应已回填");
        assertEquals(0, after.getStorageFeeUsd().compareTo(new BigDecimal("0.500000")), "合仓费应已回填");
        assertEquals(0, after.getCommissionUsd().compareTo(new BigDecimal("2.850000")), "佣金应已回填");
        assertEquals(0, after.getAvgReturnCostCny().compareTo(new BigDecimal("1.0848")), "平均退货费用应已回填（decimal 12,4）");
        assertEquals(0, after.getFbaTotalCny().compareTo(new BigDecimal("64.5001")), "FBA 总费用应已回填");
        assertEquals(0, after.getBasedPriceUsd().compareTo(new BigDecimal("18.99")), "口径售价应已记录");
        assertNotNull(after.getCalcAt(), "测算时间应已记录");

        // 关键：阶段一的值必须原样保留
        assertEquals(0, after.getInboundConfigFeeUsd().compareTo(new BigDecimal("0.320000")), "阶段二更新不应覆盖阶段一写入的入库配置费");
        assertEquals(0, after.getMonthlyStorageUsd().compareTo(new BigDecimal("0.094000")), "阶段二更新不应覆盖阶段一写入的仓储费");
    }

    @Test
    @DisplayName("唯一约束：同一产品只能有一条费用记录")
    void testUniqueProductId() {
        Product p = insertProduct();

        ProductFbaFee first = new ProductFbaFee();
        first.setProductId(p.getId());
        feeMapper.insert(first);

        ProductFbaFee second = new ProductFbaFee();
        second.setProductId(p.getId());
        assertThrows(DuplicateKeyException.class, () -> feeMapper.insert(second), "重复插入同一产品应触发唯一键冲突");
    }
}
