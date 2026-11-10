package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.mapper.DictCategoryCommissionMapper;
import com.cyxy.mapper.DictCategoryCommissionTierMapper;
import com.cyxy.mapper.DictCategoryReturnThresholdMapper;
import com.cyxy.mapper.DictFbaRateMapper;
import com.cyxy.mapper.DictFbaStepRateMapper;
import com.cyxy.mapper.DictReturnProcessingFeeMapper;
import com.cyxy.mapper.SysConfigMapper;
import com.cyxy.model.entity.DictCategoryCommission;
import com.cyxy.model.entity.DictCategoryCommissionTier;
import com.cyxy.model.entity.DictCategoryReturnThreshold;
import com.cyxy.model.entity.DictFbaRate;
import com.cyxy.model.entity.DictFbaStepRate;
import com.cyxy.model.entity.DictReturnProcessingFee;
import com.cyxy.model.entity.SysConfig;
import com.cyxy.model.vo.ReturnFeeLookupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final DictReturnProcessingFeeMapper returnFeeMapper;
    private final SysConfigMapper sysConfigMapper;

    /** 兜底汇率：只在 sys_config 缺失或值非法时启用，与前端 dict.js 的 6.68 一致 */
    private static final BigDecimal FALLBACK_EXCHANGE_RATE = new BigDecimal("6.68");

    /* ---------------- 字典缓存（volatile 快照，改动字典后调 refresh） ---------------- */

    private volatile List<DictCategoryCommission> commissionCache;
    private volatile List<DictCategoryCommissionTier> tierCache;
    private volatile List<DictCategoryReturnThreshold> thresholdCache;
    private volatile List<DictFbaRate> fbaRateCache;
    private volatile List<DictFbaStepRate> stepRateCache;
    private volatile List<DictReturnProcessingFee> returnFeeCache;

    /** 字典表改动后手动刷新缓存 */
    public void refresh() {
        commissionCache = null;
        tierCache = null;
        thresholdCache = null;
        fbaRateCache = null;
        stepRateCache = null;
        returnFeeCache = null;
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

    /* ---------------- 尾程退货处理费（按计费重自动带出） ---------------- */

    /**
     * 按「计费重」定位尾程退货费，供利润计算器自动带出输入框的值。
     *
     * <p>用户口径：取值只依据实际计费重。tierCode 在这里是<b>可选</b>的收窄条件：
     * 计算器本来就算出了尺寸分段（small / large / oversize），传进来只是把两套
     * 标准尺寸的档位分开，不需要用户额外填任何东西；不传时全表纯按计费重匹配。
     * 大件（oversize）不再按尺寸细分，纯粹按计费重落在哪个区间取。</p>
     *
     * <p>7 个分组由业务口径给定：小号标准 8 档、大号标准 13 档、大号大件、
     * 超大尺寸 0-50磅、超大件 50-70 / 70-150 / 150磅以上。
     * 原表 A 列对 50-70 / 70-150 两行写的是「0 至 70 磅」「0 至 150 磅」，
     * 但匹配边界按分组名修正为 800~1120oz、1120~2400oz，A 列原文只留展示。</p>
     *
     * <p>区间口径与 {@link #fbaRate} 一致：weight_min_oz 左开、weight_max_oz 右闭；
     * 计费重 ≤ 0 时兜底命中首档。候选按「大类顺序 + sort_no」排，取第一条命中：
     * 「大号大件」与「超大尺寸：0-50磅」重叠时稳定取表中在前的「大号大件」，
     * 不会随数据库返回顺序漂移。实际业务以亚马逊后台尺寸档为准，值仍允许手改。</p>
     *
     * <p>FORMULA 档按「起步价 + 超出起算点的部分 ÷ 步进单位（向上取整）× 加价」算，
     * 例如 large 3.5lb：5.00 + ceil((56-48)/4)×0.05 = 5.10。
     * 向上取整是因为按整个步进单位计费，不足一个单位也算一个单位。</p>
     *
     * @param tierCode     可选的大类 small / large / oversize；为空表示纯按计费重全表匹配
     * @param billWeightLb 计费重（lb，实重与体积重取大）
     */
    public ReturnFeeLookupVO lookupReturnFee(String tierCode, BigDecimal billWeightLb) {
        if (billWeightLb == null) {
            return ReturnFeeLookupVO.missed(tierCode, billWeightLb, null);
        }
        // lb → oz，统一到字典表的单位。16 是 1 磅的盎司数，与前端 UNIT_FACTORS 一致
        BigDecimal oz = billWeightLb.multiply(new BigDecimal("16"));
        boolean anyTier = tierCode == null || tierCode.isBlank();

        // selectList(null) 已被 @TableLogic 自动过滤软删行，这里不必再判 isDeleted
        DictReturnProcessingFee row = returnFees().stream()
                .filter(r -> anyTier || tierCode.equals(r.getTierCode()))
                .sorted(Comparator
                        .comparingInt((DictReturnProcessingFee r) -> tierRank(r.getTierCode()))
                        .thenComparing(r -> r.getSortNo() == null
                                ? Integer.MAX_VALUE : r.getSortNo()))
                .filter(r -> {
                    BigDecimal min = r.getWeightMinOz() == null ? BigDecimal.ZERO : r.getWeightMinOz();
                    BigDecimal max = r.getWeightMaxOz();
                    // min 左开；计费重为 0 或负数时兜底命中首档，与 fbaRate 的处理一致
                    boolean aboveMin = oz.compareTo(min) > 0 || oz.compareTo(BigDecimal.ZERO) <= 0;
                    boolean belowMax = max == null || oz.compareTo(max) <= 0;
                    return aboveMin && belowMax;
                })
                .findFirst()
                .orElse(null);
        if (row == null) {
            return ReturnFeeLookupVO.missed(tierCode, billWeightLb, oz);
        }

        BigDecimal fee = "FORMULA".equals(row.getFeeType())
                ? calcFormulaFee(row, oz)
                : row.getFeeUsd();
        if (fee != null) {
            // 展示与入参统一两位小数；calc.js 里 returnFeeUsd 也按数值直接参与计算
            fee = fee.setScale(2, RoundingMode.HALF_UP);
        }
        return new ReturnFeeLookupVO(true, tierCode, row.getGroupCode(), row.getGroupName(),
                billWeightLb, oz, fee, row.getFeeType(), row.getWeightLabel(), row.getFormulaText());
    }

    /** 大类顺序 = 原表行顺序：小号标准 → 大号标准 → 大件，保证重叠档取第一条时稳定 */
    private static int tierRank(String tierCode) {
        if ("small".equals(tierCode)) return 0;
        if ("large".equals(tierCode)) return 1;
        if ("oversize".equals(tierCode)) return 2;
        return 3;
    }

    /** FORMULA 行的续重计算；四个参数缺任何一个就返回 null，避免算出半吊子的费用 */
    private static BigDecimal calcFormulaFee(DictReturnProcessingFee row, BigDecimal oz) {
        if (row.getBaseFeeUsd() == null || row.getStepFeeUsd() == null
                || row.getStepUnit() == null || row.getStepFromOz() == null) {
            return null;
        }
        // OZ4 = 每 4 盎司一个步进；LB = 每 16 盎司（即 1 磅）一个步进
        BigDecimal stepOz = "OZ4".equals(row.getStepUnit())
                ? new BigDecimal("4") : new BigDecimal("16");
        BigDecimal extra = oz.subtract(row.getStepFromOz()).max(BigDecimal.ZERO);
        BigDecimal steps = extra.divide(stepOz, 0, RoundingMode.CEILING);
        return row.getBaseFeeUsd().add(steps.multiply(row.getStepFeeUsd()));
    }

    /* ---------------- 汇率 ---------------- */

    /**
     * 当前生效汇率：读 sys_config「最新汇率」，是【全项目唯一读入口】。
     *
     * <p>为什么不再查 dict_exchange_rate：那是时序表，职责只有记录变化 + 画趋势图。
     * 若业务侧各自 ORDER BY rate_date 取最新，迟早会冒出「A 处按日期、B 处按 id」两套口径；
     * 现在统一读全局参数，汇率同步任务在一个事务里把历史表和当前值写一致即可。</p>
     *
     * <p>为什么不缓存：这个值一天最多变两次，而且管理员手工改过 sys_config 后要立刻生效；
     * 一次主键查询成本可忽略，加缓存反而要到处记得 refresh。</p>
     *
     * <p>兜底 6.68 只在「配置行缺失 / 值不是数字」时启用，正常链路永远走不到，
     * 也绝不把兜底值写回库 —— 否则一次配置事故会悄悄污染历史数据。</p>
     *
     * <p>CONFIG_KEY 是 public static final 常量，编译期内联到本类，
     * 不会与「SyncService 注入 ProfitDictLoader」构成 Spring bean 循环依赖。</p>
     */
    public BigDecimal latestUsdCnyRate() {
        SysConfig config = sysConfigMapper.selectById(ExchangeRateSyncService.CONFIG_KEY);
        if (config == null || config.getConfigValue() == null
                || config.getConfigValue().isBlank()) {
            return FALLBACK_EXCHANGE_RATE;
        }
        try {
            BigDecimal rate = new BigDecimal(config.getConfigValue().trim());
            return rate.signum() > 0 ? rate : FALLBACK_EXCHANGE_RATE;
        } catch (NumberFormatException e) {
            return FALLBACK_EXCHANGE_RATE;
        }
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

    /** 尾程退货费字典同样全量缓存：26 行，改动后调 refresh() 清快照 */
    private List<DictReturnProcessingFee> returnFees() {
        List<DictReturnProcessingFee> snapshot = returnFeeCache;
        if (snapshot == null) {
            synchronized (this) {
                if (returnFeeCache == null) {
                    returnFeeCache = returnFeeMapper.selectList(null);
                }
                snapshot = returnFeeCache;
            }
        }
        return snapshot;
    }
}
