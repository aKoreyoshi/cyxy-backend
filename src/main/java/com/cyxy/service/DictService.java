package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.mapper.DictCategoryReturnThresholdMapper;
import com.cyxy.model.entity.DictCategoryReturnThreshold;
import com.cyxy.model.vo.DictOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author: Koreyoshi
 * @description: 字典读取服务（前端下拉用）
 * <p>
 * 产品类目取 dict_category_return_threshold，与佣金类目 dict_category_commission
 * 是两套各自独立的字典 —— 录入产品时只用到前者，后者只在计算利润时用。
 * 两者不做联动，别在代码里把它们关联起来。
 * </p>
 * <p>
 * 项目约定：字典不加 JOIN，应用层全量载入内存。这里同样走内存缓存，
 * 字典数据改动后调用 refresh() 失效。
 * </p>
 * @version: 1.0
 * @date: 2026年11月2日, 00:50:00
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private final DictCategoryReturnThresholdMapper returnThresholdMapper;

    /** 产品类目缓存 */
    private volatile List<DictOptionVO> categoryCache;

    /**
     * 产品类目下拉选项
     *
     * @return [{code: threshold_code, name: category_name}]，按 sort_no 升序
     */
    public List<DictOptionVO> getCategoryOptions() {
        List<DictOptionVO> snapshot = categoryCache;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            snapshot = categoryCache;
            if (snapshot != null) {
                return snapshot;
            }
            LocalDate today = LocalDate.now();
            List<DictCategoryReturnThreshold> rows = returnThresholdMapper.selectList(
                new LambdaQueryWrapper<DictCategoryReturnThreshold>()
                    .eq(DictCategoryReturnThreshold::getStatus, 1)
                    .le(DictCategoryReturnThreshold::getValidFrom, today)
                    // valid_to 为 NULL 表示长期有效
                    .and(w -> w
                        .isNull(DictCategoryReturnThreshold::getValidTo)
                        .or()
                        .ge(DictCategoryReturnThreshold::getValidTo, today))
                    .orderByAsc(DictCategoryReturnThreshold::getSortNo)
            );
            snapshot = rows.stream()
                .sorted(Comparator.comparing(r -> r.getSortNo() == null ? Integer.MAX_VALUE : r.getSortNo()))
                .map(r -> new DictOptionVO(r.getThresholdCode(), r.getCategoryName()))
                .collect(Collectors.toList());
            categoryCache = snapshot;
            return snapshot;
        }
    }

    /** 字典数据改动后手动刷新缓存 */
    public void refresh() {
        categoryCache = null;
    }
}
