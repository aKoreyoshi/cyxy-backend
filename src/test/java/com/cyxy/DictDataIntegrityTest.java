package com.cyxy;

import com.cyxy.mapper.*;
import com.cyxy.model.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 字典数据完整性校验
 *
 * 用途：字典数据全部来自 Excel / 前端 dict.js，人工录入或脚本导入都可能出错
 * （例如把 0.102 手滑写成 0.012 会直接导致退货费用算错一个数量级）。
 * 本测试锁定「条数 + 关键值」，任何一次字典变更导致数据漂移都会被立刻发现。
 *
 * 基准值来源：《cyxy-利润计算表格 - 副本.xlsm》与前端 dict.js，二者已核对一致。
 */
@SpringBootTest
class DictDataIntegrityTest {

    @Autowired private DictCategoryCommissionMapper commissionMapper;
    @Autowired private DictCategoryCommissionTierMapper tierMapper;
    @Autowired private DictCategoryReturnThresholdMapper thresholdMapper;
    @Autowired private DictFbaSizeTierMapper sizeTierMapper;
    @Autowired private DictFbaRateMapper fbaRateMapper;
    @Autowired private DictFbaStepRateMapper stepRateMapper;
    @Autowired private DictFirstLegMethodMapper firstLegMapper;
    @Autowired private SysConfigMapper sysConfigMapper;
    @Autowired private DictExchangeRateMapper exchangeRateMapper;

    @Test
    void dictRowCount() {
        assertEquals(46, commissionMapper.selectList(null).size(), "佣金类目应为 46 项");
        assertEquals(31, tierMapper.selectList(null).size(), "阶梯佣金档位应为 31 行");
        assertEquals(33, thresholdMapper.selectList(null).size(), "退货阈值应为 33 项");
        assertEquals(3, sizeTierMapper.selectList(null).size(), "尺寸分段应为 3 档");
        assertEquals(60, fbaRateMapper.selectList(null).size(), "FBA 费率矩阵应为 60 行");
        assertEquals(6, stepRateMapper.selectList(null).size(), "阶梯步进费率应为 6 行");
        assertEquals(5, firstLegMapper.selectList(null).size(), "头程方式应为 5 项");
        // 2026-11-01 新增 3 项：DEFAULT_TARIFF_RATE / MONTHLY_STORAGE_RATE_USD / STORAGE_TURNOVER_MONTHS
        // （原 DEFAULT_STORAGE_FEE_USD 同期改名为 INBOUND_CONFIG_FEE_USD，不计入增量）
        assertEquals(18, sysConfigMapper.selectList(null).size(), "全局参数应为 18 项");
        assertEquals(1, exchangeRateMapper.selectList(null).size(), "汇率基线应为 1 条");
    }

    @Test
    void commissionFeeModeDistribution() {
        List<DictCategoryCommission> all = commissionMapper.selectList(null);
        assertEquals(33, all.stream().filter(x -> "FLAT".equals(x.getFeeMode())).count(), "FLAT 单一费率");
        assertEquals(4, all.stream().filter(x -> "FULL".equals(x.getFeeMode())).count(), "FULL 跳档");
        assertEquals(9, all.stream().filter(x -> "MARGINAL".equals(x.getFeeMode())).count(), "MARGINAL 累进");
    }

    @Test
    void keyValues() {
        // 宠物用品：退货阈值 0.102000，佣金 FLAT 15%
        DictCategoryReturnThreshold pet = thresholdMapper.selectById("pet_supplies");
        assertNotNull(pet, "宠物用品退货阈值缺失");
        assertEquals(0, new BigDecimal("0.102000").compareTo(pet.getThresholdRate()));

        DictCategoryCommission petComm = commissionMapper.selectById("pet_supplies");
        assertNotNull(petComm, "宠物用品佣金类目缺失");
        assertEquals("FLAT", petComm.getFeeMode());
        assertEquals(0, new BigDecimal("0.1500").compareTo(petComm.getFlatRate()));

        // 艺术品为 4 档累进
        assertEquals(4, tierMapper.selectList(null).stream()
                .filter(x -> "fine_art".equals(x.getCategoryCode())).count(), "艺术品应为 4 档累进");

        // 小号标准 ≤2oz 的低价格带费率（2026-01-15 版）
        DictFbaRate first = fbaRateMapper.selectList(null).stream()
                .filter(x -> "small".equals(x.getTierCode()) && "LOW".equals(x.getPriceBand())
                        && x.getWeightMaxOz().compareTo(new BigDecimal("2")) == 0)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2.62").compareTo(first.getRateUsd()));

        // 大号标准 3~20lb 步进：低价格带起步价 6.69，每 4oz 加 0.08
        DictFbaStepRate step = stepRateMapper.selectList(null).stream()
                .filter(x -> "large".equals(x.getTierCode()) && "LOW".equals(x.getPriceBand()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("6.69").compareTo(step.getBaseUsd()));
        assertEquals(0, new BigDecimal("0.0800").compareTo(step.getStepUsd()));

        // 汇率基线
        DictExchangeRate rate = exchangeRateMapper.selectList(null).get(0);
        assertEquals("USD/CNY", rate.getCurrencyPair());
        assertEquals(0, new BigDecimal("6.68").compareTo(rate.getRate()));
        assertEquals("2026-10-30", rate.getRateDate().toString());
    }

    @Test
    void sysConfigKeys() {
        List<SysConfig> all = sysConfigMapper.selectList(null);
        assertTrue(all.stream().anyMatch(x -> "FUEL_SURCHARGE_RATE".equals(x.getConfigKey())
                && "0.035".equals(x.getConfigValue())), "燃油附加应为 0.035");
        assertTrue(all.stream().anyMatch(x -> "DIM_DIVISOR_INCH".equals(x.getConfigKey())
                && "139".equals(x.getConfigValue())), "体积重除数应为 139");
        assertTrue(all.stream().anyMatch(x -> "LARGE_STANDARD_OZ_LIMIT".equals(x.getConfigKey())
                && "48".equals(x.getConfigValue())), "大号标准查表上限应为 48oz");
    }
}
