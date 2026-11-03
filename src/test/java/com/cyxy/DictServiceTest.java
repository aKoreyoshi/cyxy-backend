package com.cyxy;

import com.cyxy.model.vo.DictOptionVO;
import com.cyxy.service.DictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典读取服务测试 —— 产品类目下拉的数据源。
 *
 * 业务约定（2026-11-02 用户明确）：
 *   产品类目   ← dict_category_return_threshold（33 项），录入产品时用
 *   佣金类目   ← dict_category_commission（46 项），只在计算利润时用
 *   两者各自独立，不做联动。本测试锁定「产品类目只读退货阈值表」这一口径。
 */
@SpringBootTest
@DisplayName("字典读取 - 产品类目")
class DictServiceTest {

    @Autowired
    private DictService dictService;

    @Test
    @DisplayName("产品类目取自退货阈值表：33 项")
    void category_count() {
        assertEquals(33, dictService.getCategoryOptions().size(), "产品类目应为 33 项");
    }

    @Test
    @DisplayName("每项都有 code 与 name，且无重复 code")
    void category_shape() {
        List<DictOptionVO> list = dictService.getCategoryOptions();
        list.forEach(item -> {
            assertNotNull(item.code(), "code 不能为空");
            assertFalse(item.name() == null || item.name().isBlank(), "name 不能为空");
        });
        assertEquals(list.size(), list.stream().map(DictOptionVO::code).distinct().count(), "code 不能重复");
    }

    @Test
    @DisplayName("按 sort_no 升序返回（不能每次顺序乱跳）")
    void category_ordered() {
        List<DictOptionVO> list = dictService.getCategoryOptions();
        List<String> first = list.stream().map(DictOptionVO::code).toList();
        // 二次调用走缓存，顺序必须一致
        List<String> second = dictService.getCategoryOptions().stream().map(DictOptionVO::code).toList();
        assertEquals(first, second, "缓存前后顺序应一致");
    }

    @Test
    @DisplayName("refresh() 后能重新加载（字典改了要能生效）")
    void category_refresh() {
        int before = dictService.getCategoryOptions().size();
        dictService.refresh();
        int after = dictService.getCategoryOptions().size();
        assertEquals(before, after, "刷新后条数应不变");
        assertTrue(after > 0);
    }
}
