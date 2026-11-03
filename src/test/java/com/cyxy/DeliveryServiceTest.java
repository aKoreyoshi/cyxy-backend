package com.cyxy;

import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.ProductMapper;
import com.cyxy.model.entity.Product;
import com.cyxy.model.enums.DeliveryStatus;
import com.cyxy.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配送状态测试：确认送达 / 撤销确认 / 确认后锁定。
 *
 * 全程 @Transactional，测试结束回滚，不在库里留数据。
 * 判定口径按自然日（同一天 = 准时），时间统一北京时间。
 */
@SpringBootTest
@Transactional
@DisplayName("配送状态：确认送达与撤销")
class DeliveryServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 直接插库造数据，必须补齐库里所有 NOT NULL 且无默认值的列：
     * product_no / name_cn / main_image / source_url / purchase_price /
     * avg_domestic_shipping / net_weight_g / packaged_weight_g / length_cm / width_cm / height_cm
     */
    private Product newProduct(String no) {
        Product p = new Product();
        p.setProductNo(no);
        p.setNameCn("配送测试商品-" + no);
        p.setMainImage("");
        p.setSourceUrl("https://example.com/" + no);
        p.setPurchasePrice(new BigDecimal("10.00"));
        p.setAvgDomesticShipping(new BigDecimal("2.00"));
        p.setNetWeightG(new BigDecimal("200.00"));
        p.setPackagedWeightG(new BigDecimal("250.00"));
        p.setLengthCm(new BigDecimal("20.00"));
        p.setWidthCm(new BigDecimal("15.00"));
        p.setHeightCm(new BigDecimal("5.00"));
        p.setListPriceUsd(BigDecimal.ZERO);
        p.setStatus("DRAFT");
        productMapper.insert(p);
        return p;
    }

    @Test
    @DisplayName("天数差判定：负提前 / 零准时 / 正晚送达")
    void judgeByDays() {
        assertEquals(DeliveryStatus.EARLY, DeliveryStatus.judge(-2));
        assertEquals(DeliveryStatus.ON_TIME, DeliveryStatus.judge(0));
        assertEquals(DeliveryStatus.LATE, DeliveryStatus.judge(3));
        assertTrue(DeliveryStatus.EARLY.isDelivered());
        assertTrue(!DeliveryStatus.IN_TRANSIT.isDelivered());
    }

    @Test
    @DisplayName("没填预计送达时间不能确认")
    void confirmWithoutExpectedDate() {
        Product p = newProduct("TEST-DLV-001");
        assertNull(p.getExpectedDeliveryDate());

        CYXYException ex = assertThrows(CYXYException.class, () -> productService.confirmDelivery(p.getId()));
        assertTrue(ex.getMessage().contains("预计送达时间"));
    }

    @Test
    @DisplayName("预计日期是今天 → 判定为准时送达")
    void confirmOnTime() {
        Product p = newProduct("TEST-DLV-002");
        p.setExpectedDeliveryDate(LocalDate.now());
        productMapper.updateById(p);

        Product done = productService.confirmDelivery(p.getId());
        assertEquals(DeliveryStatus.ON_TIME.getCode(), done.getDeliveryStatus());
        assertEquals(0, done.getDelayDays());
        assertNotNull(done.getActualDeliveryTime());
    }

    @Test
    @DisplayName("预计日期在过去 → 判定为晚送达，天数差为正")
    void confirmLate() {
        Product p = newProduct("TEST-DLV-003");
        p.setExpectedDeliveryDate(LocalDate.now().minusDays(5));
        productMapper.updateById(p);

        Product done = productService.confirmDelivery(p.getId());
        assertEquals(DeliveryStatus.LATE.getCode(), done.getDeliveryStatus());
        assertEquals(5, done.getDelayDays());
    }

    @Test
    @DisplayName("预计日期在未来 → 判定为提前送达，天数差为负")
    void confirmEarly() {
        Product p = newProduct("TEST-DLV-004");
        p.setExpectedDeliveryDate(LocalDate.now().plusDays(3));
        productMapper.updateById(p);

        Product done = productService.confirmDelivery(p.getId());
        assertEquals(DeliveryStatus.EARLY.getCode(), done.getDeliveryStatus());
        assertEquals(-3, done.getDelayDays());
    }

    @Test
    @DisplayName("已确认送达不能重复确认")
    void cannotConfirmTwice() {
        Product p = newProduct("TEST-DLV-005");
        p.setExpectedDeliveryDate(LocalDate.now());
        productMapper.updateById(p);
        productService.confirmDelivery(p.getId());

        CYXYException ex = assertThrows(CYXYException.class, () -> productService.confirmDelivery(p.getId()));
        assertTrue(ex.getMessage().contains("重复"));
    }

    @Test
    @DisplayName("确认后改预计送达时间无效（锁定）")
    void lockedAfterConfirm() {
        Product p = newProduct("TEST-DLV-006");
        LocalDate original = LocalDate.now().minusDays(2);
        p.setExpectedDeliveryDate(original);
        productMapper.updateById(p);
        Product confirmed = productService.confirmDelivery(p.getId());
        assertEquals(DeliveryStatus.LATE.getCode(), confirmed.getDeliveryStatus());

        // 模拟详情页全量提交：前端把预计送达时间改成了别的日期
        Product payload = productService.showDetails(p.getId());
        payload.setExpectedDeliveryDate(LocalDate.now().plusDays(9));
        payload.setDeliveryStatus(DeliveryStatus.EARLY.getCode());
        payload.setDelayDays(-9);
        payload.setRemark("顺手改个备注");

        Product after = productService.updateProduct(payload);
        assertEquals(original, after.getExpectedDeliveryDate(), "已确认后预计送达时间不应被改掉");
        assertEquals(DeliveryStatus.LATE.getCode(), after.getDeliveryStatus(), "已确认后状态不应被改掉");
        assertEquals(2, after.getDelayDays());
        assertEquals("顺手改个备注", after.getRemark(), "其它字段仍应正常保存");
        assertNotNull(after.getActualDeliveryTime());
    }

    @Test
    @DisplayName("撤销确认后回到配送中，且可以再次确认")
    void cancelThenConfirmAgain() {
        Product p = newProduct("TEST-DLV-007");
        p.setExpectedDeliveryDate(LocalDate.now());
        productMapper.updateById(p);
        productService.confirmDelivery(p.getId());

        Product back = productService.cancelDelivery(p.getId());
        assertNull(back.getActualDeliveryTime(), "撤销后实际送达时间应清空");
        assertNull(back.getDelayDays());
        assertEquals(DeliveryStatus.IN_TRANSIT.getCode(), back.getDeliveryStatus());

        // 撤销后预计送达时间可改，改完再确认按新日期判定
        LocalDate future = LocalDate.now().plusDays(4);
        Product payload = productService.showDetails(p.getId());
        payload.setExpectedDeliveryDate(future);
        productService.updateProduct(payload);

        Product again = productService.confirmDelivery(p.getId());
        assertEquals(DeliveryStatus.EARLY.getCode(), again.getDeliveryStatus());
        assertEquals(-4, again.getDelayDays());
    }

    @Test
    @DisplayName("没确认过不能撤销")
    void cannotCancelWhenNotConfirmed() {
        Product p = newProduct("TEST-DLV-008");
        p.setExpectedDeliveryDate(LocalDate.now());
        productMapper.updateById(p);

        CYXYException ex = assertThrows(CYXYException.class, () -> productService.cancelDelivery(p.getId()));
        assertTrue(ex.getMessage().contains("尚未确认"));
    }

    @Test
    @DisplayName("保存产品时配送状态自动维护：填了=配送中，没填=未填写")
    void statusAutoMaintain() {
        Product p = newProduct("TEST-DLV-009");
        // 直接走 mapper 插入，没经过 service 的 normalize，此时状态还没维护
        assertNull(productService.showDetails(p.getId()).getDeliveryStatus());

        p.setExpectedDeliveryDate(LocalDate.now().plusDays(1));
        productService.updateProduct(p);
        assertEquals(DeliveryStatus.IN_TRANSIT.getCode(),
                productService.showDetails(p.getId()).getDeliveryStatus());

        Product payload = productService.showDetails(p.getId());
        payload.setExpectedDeliveryDate(null);
        productService.updateProduct(payload);
        assertEquals(DeliveryStatus.NOT_SET.getCode(),
                productService.showDetails(p.getId()).getDeliveryStatus());
    }

    @Test
    @DisplayName("天数差与自然日口径一致：跨月也能算对")
    void daysAcrossMonth() {
        LocalDate from = LocalDate.of(2026, 10, 30);
        LocalDate to = LocalDate.of(2026, 11, 3);
        assertEquals(4, ChronoUnit.DAYS.between(from, to));
    }
}
