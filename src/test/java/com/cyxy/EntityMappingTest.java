package com.cyxy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.cyxy.mapper.ProductMapper;
import com.cyxy.model.entity.DictFirstLegMethod;
import com.cyxy.model.entity.DictFbaRate;
import com.cyxy.model.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体类与数据库表的映射冒烟测试。
 * 全程标注 @Transactional，测试结束自动回滚，不会在库中留下数据。
 */
@SpringBootTest
@Transactional
class EntityMappingTest {

    @Autowired
    private ProductMapper productMapper;

    private Product buildProduct() {
        Product p = new Product();
        p.setProductNo("TEST-0001");
        p.setNameCn("测试商品");
        p.setNameEn("Test Product");
        p.setMainImage("https://example.com/a.png");
        p.setCategoryCode("PET_SUPPLIES");
        p.setCompetitorAsin("B0TEST00001");
        p.setSourceUrl("https://1688.com/test.html");
        p.setSupplier("测试供应商");
        p.setPurchasePrice(new BigDecimal("13.5000"));
        p.setMoq(100);
        p.setPackageCost(new BigDecimal("1.5000"));
        p.setAvgDomesticShipping(new BigDecimal("1.2000"));
        p.setNetWeightG(new BigDecimal("205.00"));
        p.setPackagedWeightG(new BigDecimal("220.00"));
        p.setLengthCm(new BigDecimal("24.00"));
        p.setWidthCm(new BigDecimal("22.50"));
        p.setHeightCm(new BigDecimal("2.00"));
        p.setFirstLegMethodCode("SEA");
        p.setLogisticsProvider("云途");
        p.setFirstLegUnitPrice(new BigDecimal("8.00"));
        p.setListPriceUsd(new BigDecimal("18.99"));
        p.setCompetitorPriceUsd(new BigDecimal("21.99"));
        p.setMonthlySalesEst(500);
        p.setFirstBatchQty(1000);
        p.setStatus("DRAFT");
        p.setRemark("冒烟测试用数据");
        return p;
    }

    @Test
    @DisplayName("Product 全类型字段往返读写：自增主键、BigDecimal、LocalDateTime、软删标记")
    void testProductRoundTrip() {
        long before = productMapper.selectCount(null);

        Product p = buildProduct();
        int rows = productMapper.insert(p);
        assertEquals(1, rows, "插入应影响 1 行");
        assertNotNull(p.getId(), "自增主键应回填到实体");

        Product saved = productMapper.selectById(p.getId());
        assertNotNull(saved, "按 id 应能查回");

        assertEquals(0, saved.getPurchasePrice().compareTo(new BigDecimal("13.5000")), "decimal(12,4) 应精确保留 4 位小数");
        assertEquals(0, saved.getPackagedWeightG().compareTo(new BigDecimal("220.00")), "含包装实重应保持两位小数");
        assertEquals(0, saved.getListPriceUsd().compareTo(new BigDecimal("18.99")), "美元定价应无损");
        assertEquals("测试商品", saved.getNameCn(), "中文应正常读写");
        assertEquals(Integer.valueOf(100), saved.getMoq(), "整数型应正确映射");
        assertEquals("DRAFT", saved.getStatus(), "默认值列应正确写出");

        assertNotNull(saved.getCreatedTime(), "created_time 应由数据库默认值填充");
        assertNotNull(saved.getUpdatedTime(), "updated_time 应由数据库默认值填充");
        assertEquals(Integer.valueOf(0), saved.getIsDeleted(), "新记录软删标记应为 0");

        long after = productMapper.selectCount(null);
        assertEquals(before + 1, after, "插入后总行数应加 1");
    }

    @Test
    @DisplayName("逻辑删除：deleteById 应转为 UPDATE is_deleted=1，而非物理删除")
    void testLogicDelete() {
        Product p = buildProduct();
        productMapper.insert(p);
        Long id = p.getId();

        int rows = productMapper.deleteById(id);
        assertEquals(1, rows, "删除应影响 1 行");

        assertNull(productMapper.selectById(id), "软删后 selectById 应查不到");

        long remain = productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getId, id));
        assertEquals(0, remain, "软删后条件查询也应排除该行");
    }

    @Test
    @DisplayName("分页插件：selectPage 应自动生成 count 查询并返回分页信息")
    void testPagination() {
        productMapper.insert(buildProduct());

        IPage<Product> page = productMapper.selectPage(new Page<>(1, 5), Wrappers.emptyWrapper());
        assertTrue(page.getTotal() >= 1, "分页总数应至少为 1");
        assertNotNull(page.getRecords(), "分页记录集不应为空");
        assertEquals(1, page.getCurrent(), "当前页应为 1");
    }

    @Test
    @DisplayName("不建 Mapper 的字典表可用 Db 工具类查询（头程方式 / FBA 费率）")
    void testDbToolkitWithoutMapper() {
        List<DictFirstLegMethod> methods = Db.lambdaQuery(DictFirstLegMethod.class).list();
        assertNotNull(methods, "未建 Mapper 的字典表也应能通过 Db 查询");

        List<DictFbaRate> rates = Db.lambdaQuery(DictFbaRate.class)
                .eq(DictFbaRate::getTierCode, "small_standard")
                .list();
        assertNotNull(rates, "FBA 费率表应可通过 lambda 条件查询");
    }
}
