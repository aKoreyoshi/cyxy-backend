package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.mapper.DictFbaSizeTierMapper;
import com.cyxy.model.entity.DictFbaSizeTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * @author: Koreyoshi
 * @description: FBA 尺寸分段解析器
 * 由「包装长/宽/高 cm + 含包装实重 g」自动匹配 dict_fba_size_tier.tier_code。
 * <p>
 * 三条设计约定：
 * 1. 阈值全部来自 dict_fba_size_tier 表，不在 Java 里硬编码。亚马逊每年调价，
 *    改字典即可，不用改代码。
 * 2. 字典按 sortNo 升序判定，第一个满足全部上限的档位即为结果；
 *    四个上限全为 NULL 的档位（oversize）不参与匹配，作为兜底。
 * 3. 判定用实重。字典里的 weightBasis 只影响「查费率」时取实重还是计费重，
 *    不影响分段判定本身——口径与前端 calc.js resolveSizeTier 保持一致。
 * </p>
 * @version: 1.0
 * @date: 2026年11月2日, 00:10:00
 */
@Service
@RequiredArgsConstructor
public class SizeTierService {

    /** cm → inch，与前端 dict.js UNIT_FACTORS.cmToInch 一致 */
    private static final BigDecimal CM_TO_INCH = new BigDecimal("0.3937008");
    /** kg → lb，与前端 dict.js UNIT_FACTORS.kgToLb 一致 */
    private static final BigDecimal KG_TO_LB = new BigDecimal("2.2046226");
    /** g → kg */
    private static final BigDecimal G_TO_KG = new BigDecimal("0.001");
    /** 尺寸换算后保留 2 位小数，与前端 round(x, 2) 对齐，避免边界值判定漂移 */
    private static final int INCH_SCALE = 2;

    private final DictFbaSizeTierMapper sizeTierMapper;

    /** 字典全量缓存：字典表改动后调用 refresh() 失效 */
    private volatile List<DictFbaSizeTier> cache;

    /**
     * 匹配尺寸分段
     *
     * @param lengthCm         包装长 cm
     * @param widthCm          包装宽 cm
     * @param heightCm         包装高 cm
     * @param packagedWeightG  含包装实重 g
     * @return tier_code（small / large / oversize）；入参缺失或字典为空时返回 null
     */
    public String resolve(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, BigDecimal packagedWeightG) {
        if (lengthCm == null || widthCm == null || heightCm == null || packagedWeightG == null) {
            return null;
        }

        List<DictFbaSizeTier> tiers = tiers();
        if (tiers.isEmpty()) {
            return null;
        }

        BigDecimal l = toInch(lengthCm);
        BigDecimal w = toInch(widthCm);
        BigDecimal h = toInch(heightCm);
        BigDecimal weightLb = packagedWeightG.multiply(G_TO_KG).multiply(KG_TO_LB);

        for (DictFbaSizeTier tier : tiers) {
            if (!hasFullLimits(tier)) {
                continue; // 兜底档（上限全 NULL）不参与匹配
            }
            if (l.compareTo(tier.getMaxLengthInch()) <= 0
                && w.compareTo(tier.getMaxWidthInch()) <= 0
                && h.compareTo(tier.getMaxHeightInch()) <= 0
                && weightLb.compareTo(tier.getMaxWeightLb()) <= 0) {
                return tier.getTierCode();
            }
        }

        // 没有任何档位满足 → 取排序最后的档位兜底（oversize）
        return tiers.get(tiers.size() - 1).getTierCode();
    }

    /** 字典改动后手动刷新缓存 */
    public void refresh() {
        cache = null;
    }

    /** 字典全量载入内存（项目约定：字典不加 JOIN，应用层缓存） */
    private List<DictFbaSizeTier> tiers() {
        List<DictFbaSizeTier> snapshot = cache;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = cache;
                if (snapshot == null) {
                    snapshot = sizeTierMapper.selectList(
                        new LambdaQueryWrapper<DictFbaSizeTier>()
                            .orderByAsc(DictFbaSizeTier::getSortNo)
                    );
                    snapshot.sort(Comparator.comparing(t -> t.getSortNo() == null ? Integer.MAX_VALUE : t.getSortNo()));
                    cache = snapshot;
                }
            }
        }
        return snapshot;
    }

    private static boolean hasFullLimits(DictFbaSizeTier tier) {
        return tier.getMaxLengthInch() != null
            && tier.getMaxWidthInch() != null
            && tier.getMaxHeightInch() != null
            && tier.getMaxWeightLb() != null;
    }

    private static BigDecimal toInch(BigDecimal cm) {
        return cm.multiply(CM_TO_INCH).setScale(INCH_SCALE, RoundingMode.HALF_UP);
    }
}
