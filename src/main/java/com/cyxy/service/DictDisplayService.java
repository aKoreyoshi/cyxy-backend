package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.*;
import com.cyxy.model.entity.*;
import com.cyxy.model.vo.CommissionTreeNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典「参数列表」展示服务。
 *
 * <p>与 {@link DictService} 的区别（重要，别混用）：</p>
 * <ul>
 *   <li>DictService 供下拉框用：只返回 code/name，按 status=1 + 有效期内过滤，带内存缓存；</li>
 *   <li>本服务供参数展示页用：返回原始行数据，<b>不过滤停用/过期</b>（展示要看全貌，
 *       停用与失效条目照样列出、由前端打标签），不缓存。</li>
 * </ul>
 *
 * <p>type 白名单用 switch 枚举实现，绝不把参数拼进 SQL，天然防注入。</p>
 *
 * @author: Koreyoshi
 */
@Service
@RequiredArgsConstructor
public class DictDisplayService {

    private final DictCategoryReturnThresholdMapper returnThresholdMapper;
    private final DictFbaSizeTierMapper sizeTierMapper;
    private final DictFbaRateMapper fbaRateMapper;
    private final DictFirstLegMethodMapper firstLegMethodMapper;
    private final DictFbaStepRateMapper stepRateMapper;
    private final DictReturnProcessingFeeMapper returnFeeMapper;
    private final SysConfigMapper sysConfigMapper;
    private final DictCategoryCommissionMapper commissionMapper;
    private final DictCategoryCommissionTierMapper commissionTierMapper;
    private final DictExchangeRateMapper exchangeRateMapper;

    /**
     * 通用二维表数据。type 与前端路由末段一致，见下方 case。
     *
     * @return 原始 Entity 列表（全局 SNAKE_CASE 会自动把字段转成 snake_case）
     */
    public List<?> table(String type) {
        if (type == null || type.isBlank()) {
            throw new CYXYException("缺少字典类型");
        }
        return switch (type) {
            case "return-threshold" -> (List<?>) returnThresholdMapper.selectList(null);
            case "size-tier" -> (List<?>) sizeTierMapper.selectList(null);
            case "fba-rate" -> (List<?>) fbaRateMapper.selectList(null);
            case "first-leg" -> (List<?>) firstLegMethodMapper.selectList(null);
            case "step-rate" -> (List<?>) stepRateMapper.selectList(null);
            case "return-fee" -> (List<?>) returnFeeMapper.selectList(null);
            case "sys-config" -> (List<?>) sysConfigMapper.selectList(null);
            default -> throw new CYXYException("不支持的字典类型：" + type);
        };
    }

    /**
     * 佣金费率树：父 = 佣金类目（46 项），子 = 阶梯档位。
     * 只有 FULL / MARGINAL 类目在 tier 表里有档位，FLAT 类目是普通叶子行。
     */
    public List<CommissionTreeNodeVO> commissionTree() {
        List<DictCategoryCommission> categories = commissionMapper.selectList(
            new LambdaQueryWrapper<DictCategoryCommission>()
                .orderByAsc(DictCategoryCommission::getSortNo));

        List<DictCategoryCommissionTier> tiers = commissionTierMapper.selectList(
            new LambdaQueryWrapper<DictCategoryCommissionTier>()
                .orderByAsc(DictCategoryCommissionTier::getCategoryCode,
                    DictCategoryCommissionTier::getTierNo));

        Map<String, List<DictCategoryCommissionTier>> tierByCategory = tiers.stream()
            .filter(t -> t.getCategoryCode() != null)
            .collect(Collectors.groupingBy(DictCategoryCommissionTier::getCategoryCode));

        return categories.stream().map(c -> {
            CommissionTreeNodeVO node = new CommissionTreeNodeVO();
            node.setRowKey("c:" + c.getCategoryCode());
            node.setName(c.getCategoryName());
            node.setCode(c.getCategoryCode());
            node.setFeeMode(c.getFeeMode() == null ? null : c.getFeeMode().toUpperCase());
            node.setRate(c.getFlatRate());
            node.setMinCommissionUsd(c.getMinCommissionUsd());
            node.setNeedApproval(c.getNeedApproval());
            node.setStatus(c.getStatus());
            node.setValidFrom(c.getValidFrom());
            node.setValidTo(c.getValidTo());
            node.setRemark(c.getRemark());

            List<DictCategoryCommissionTier> sub = tierByCategory.get(c.getCategoryCode());
            if (sub != null && !sub.isEmpty()) {
                node.setChildren(sub.stream().map(t -> {
                    CommissionTreeNodeVO child = new CommissionTreeNodeVO();
                    child.setRowKey("t:" + c.getCategoryCode() + ":" + t.getTierNo());
                    child.setName("第 " + t.getTierNo() + " 档");
                    child.setCode("");
                    child.setPriceRange(formatRange(t.getPriceMinUsd(), t.getPriceMaxUsd()));
                    child.setRate(t.getTierRate());
                    child.setMinCommissionUsd(t.getMinCommissionUsd());
                    child.setRemark(t.getRemark());
                    return child;
                }).toList());
            }
            return node;
        }).toList();
    }

    /** 汇率时序，按业务日期升序 —— 表格与折线图共用这一份数据 */
    public List<DictExchangeRate> exchangeRates() {
        return exchangeRateMapper.selectList(
            new LambdaQueryWrapper<DictExchangeRate>()
                .orderByAsc(DictExchangeRate::getRateDate));
    }

    /** "$0 ~ $100" / "$100 以上"（price_max_usd 为 NULL 表示无上限） */
    private String formatRange(BigDecimal min, BigDecimal max) {
        String lo = min == null ? "0" : min.stripTrailingZeros().toPlainString();
        if (max == null) {
            return "$" + lo + " 以上";
        }
        return "$" + lo + " ~ $" + max.stripTrailingZeros().toPlainString();
    }
}
