package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.mapper.DictCategoryCommissionMapper;
import com.cyxy.mapper.DictCategoryCommissionTierMapper;
import com.cyxy.mapper.DictCategoryReturnThresholdMapper;
import com.cyxy.mapper.DictExchangeRateMapper;
import com.cyxy.mapper.DictFbaRateMapper;
import com.cyxy.mapper.DictFbaStepRateMapper;
import com.cyxy.model.entity.DictCategoryCommission;
import com.cyxy.model.entity.DictCategoryCommissionTier;
import com.cyxy.model.entity.DictCategoryReturnThreshold;
import com.cyxy.model.entity.DictExchangeRate;
import com.cyxy.model.entity.DictFbaRate;
import com.cyxy.model.entity.DictFbaStepRate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * @author: Koreyoshi
 * @description: 利润计算器的字典加载器
 * 前端 dict.js 是一份静态快照，Java 侧的唯一真源是数据库字典表。
 * 这里集中做「按业务条件定位一条费率/阈值」的查询，供 ProfitCalcService 调用。
 * <p>
 * 口径与前端 calc.js / dict.js 逐条对齐：
 * - 佣金类目可按「名称或编码」命中（前端下拉传名称，产品/历史数据可能是编码）
 * - FBA 费率行定位：weight_min_oz 左开、weight_max_oz 右闭
 * - 大号标准超过 48oz 走 dict_fba_step_rate 的 STEP_4OZ；大件走 FIRST_POUND_PLUS
 * </p>
 * 字典数据量小且稳定（60 行以内），按项目约定全量缓存于内存，不做 JOIN。
 * @version: 1.0
 * @date: 2026年11月04日, 00:00:00
 */
@Service
@RequiredArgsConstructor
public class ProfitDictLoader {

    private final DictCategoryCommissionMapper commissionMapper;
    private final DictCategoryCommissionTierMapper tierMapper;
    private final DictCategoryReturnThresholdMapper thresholdMapper;
    private final DictFbaRateMapper fbaRateMapper;
    private final DictFbaStepRateMapper stepRateMapper;
    private final DictExchangeRateMapper exchangeRateMapper;

    /* ---------------- 字典缓存（volatile 快照，改动字典后调 refresh） ---------------- */

    private volatile List<DictCategoryCommission> commissionCache;
    private volatile List<DictCategoryCommissionTier> tierCache;
    private volatile List<DictCategoryReturnThreshold> thresholdCache;
    private volatile List<DictFbaRate> fbaRateCache;
    private volatile List<DictFbaStepRate> stepRateCache;

    /** 字典表改动后手动刷新缓存 */
    public void refresh() {
        commissionCache = null;
        tierCache = null;
        thresholdCache = null;
        fbaRateCache = null;
        stepRateCache = null;
    }

    /* ---------------- 佣金类目 ---------------- */

    /** 按名称或编码取启用中的佣金类目，找不到返回 null */
    public DictCategoryCommission commission(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.isBlank()) {
            return null;
        }
        return commissions().stream()
                .filter(c -> nameOrCode.equals(c.getCategoryName()) || nameOrCode.equals(c.getCategoryCode()))
                .filter(c -> c.getStatus() == null || c.getStatus() == 1)
                .findFirst()
                .orElse(null);
    }

    /** 某类目的阶梯档位（按 tierNo 升序），FLAT 类目返回空列表 */
    public List<DictCategoryCommissionTier> tiers(String categoryCode) {
        if (categoryCode == null) {
            return List.of();
        }
        return tiers().stream()
                .filter(t -> categoryCode.equals(t.getCategoryCode()))
                .sorted(Comparator.comparing(t -> t.getTierNo() == null ? Integer.MAX_VALUE : t.getTierNo()))
                .toList();
    }

    /* ---------------- 退货阈值 ---------------- */

    /** 按名称或编码取退货阈值（小数形式，0.102 表示 10.2%），找不到返回 0（与前端 findReturnThreshold 一致） */
    public BigDecimal returnThreshold(String nameOrCode) {
        DictCategoryReturnThreshold t = threshold(nameOrCode);
        return t == null || t.getThresholdRate() == null ? BigDecimal.ZERO : t.getThresholdRate();
    }

    /** 按名称或编码取退货阈值字典行，找不到返回 null */
    public DictCategoryReturnThreshold threshold(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.isBlank()) {
            return null;
        }
        return thresholds().stream()
                .filter(t -> nameOrCode.equals(t.getCategoryName()) || nameOrCode.equals(t.getThresholdCode()))
                .filter(t -> t.getStatus() == null || t.getStatus() == 1)
                .findFirst()
                .orElse(null);
    }

    /* ---------------- FBA 费率 ---------------- */

    /**
     * 行式费率表定位：tierCode + priceBand + 重量 oz 落在 (weightMinOz, weightMaxOz]。
     * 表中各档从 0 起连续排布，故直接取「第一个 weightMaxOz ≥ oz」的行，
     * oz ≤ 0 时命中首行，与前端 clamp(ceil(oz/步长), 1, n) 的语义等价。
     */
    public DictFbaRate fbaRate(String tierCode, String priceBand, double oz) {
        List<DictFbaRate> rows = fbaRates().stream()
                .filter(r -> tierCode.equals(r.getTierCode()) && priceBand.equals(r.getPriceBand()))
                .sorted(Comparator.comparing(r -> r.getWeightMaxOz() == null
                        ? BigDecimal.valueOf(Double.MAX_VALUE) : r.getWeightMaxOz()))
                .toList();
        for (DictFbaRate row : rows) {
            if (row.getWeightMaxOz() == null
                    || BigDecimal.valueOf(oz).compareTo(row.getWeightMaxOz()) <= 0) {
                return row;
            }
        }
        return rows.isEmpty() ? null : rows.get(rows.size() - 1);
    }

    /** 取阶梯步进 / 大件计费行（按 rateType 区分），找不到返回 null */
    public DictFbaStepRate stepRate(String tierCode, String priceBand, String rateType) {
        return stepRates().stream()
                .filter(r -> tierCode.equals(r.getTierCode())
                        && priceBand.equals(r.getPriceBand())
                        && rateType.equals(r.getRateType()))
                .findFirst()
                .orElse(null);
    }

    /* ---------------- 汇率 ---------------- */

    /** 最新一条 USD/CNY 汇率，取不到时返回默认 6.68（与前端 DEFAULT_EXCHANGE_RATE 一致） */
    public BigDecimal latestUsdCnyRate() {
        List<DictExchangeRate> rows = exchangeRateMapper.selectList(
                new LambdaQueryWrapper<DictExchangeRate>()
                        .eq(DictExchangeRate::getCurrencyPair, "USD/CNY")
                        .orderByDesc(DictExchangeRate::getRateDate)
                        .last("limit 1"));
        if (rows.isEmpty() || rows.get(0).getRate() == null) {
            return new BigDecimal("6.68");
        }
        return rows.get(0).getRate();
    }

    /* ---------------- 缓存装载 ---------------- */

    private List<DictCategoryCommission> commissions() {
        List<DictCategoryCommission> snapshot = commissionCache;
        if (snapshot == null) {
            synchronized (this) {
                if (commissionCache == null) {
                    commissionCache = commissionMapper.selectList(null);
                }
                snapshot = commissionCache;
            }
        }
        return snapshot;
    }

    private List<DictCategoryCommissionTier> tiers() {
        List<DictCategoryCommissionTier> snapshot = tierCache;
        if (snapshot == null) {
            synchronized (this) {
                if (tierCache == null) {
                    tierCache = tierMapper.selectList(null);
                }
                snapshot = tierCache;
            }
        }
        return snapshot;
    }

    private List<DictCategoryReturnThreshold> thresholds() {
        List<DictCategoryReturnThreshold> snapshot = thresholdCache;
        if (snapshot == null) {
            synchronized (this) {
                if (thresholdCache == null) {
                    thresholdCache = thresholdMapper.selectList(null);
                }
                snapshot = thresholdCache;
            }
        }
        return snapshot;
    }

    private List<DictFbaRate> fbaRates() {
        List<DictFbaRate> snapshot = fbaRateCache;
        if (snapshot == null) {
            synchronized (this) {
                if (fbaRateCache == null) {
                    fbaRateCache = fbaRateMapper.selectList(null);
                }
                snapshot = fbaRateCache;
            }
        }
        return snapshot;
    }

    private List<DictFbaStepRate> stepRates() {
        List<DictFbaStepRate> snapshot = stepRateCache;
        if (snapshot == null) {
            synchronized (this) {
                if (stepRateCache == null) {
                    stepRateCache = stepRateMapper.selectList(null);
                }
                snapshot = stepRateCache;
            }
        }
        return snapshot;
    }
}
