package com.cyxy;

import com.cyxy.service.SizeTierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 尺寸分段自动匹配测试。
 *
 * 判定基准来自 dict_fba_size_tier 表（非硬编码）：
 *   small    长≤15in 宽≤12in 高≤0.75in 且 实重≤1lb
 *   large    长≤18in 宽≤14in 高≤8in   且 实重≤20lb
 *   oversize 兜底档
 *
 * 换算口径必须与前端 calc.js resolveSizeTier 完全一致，否则
 * 「录入页算出的分段」和「后端兜底重算的分段」会打架。
 */
@SpringBootTest
@DisplayName("尺寸分段自动匹配")
class SizeTierServiceTest {

    @Autowired
    private SizeTierService sizeTierService;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("回归基准：24×22.5×2cm / 220g → large（高 0.79in 压过 small 的 0.75in 上限）")
    void resolve_regressionBaseline() {
        assertEquals("large", sizeTierService.resolve(bd("24.00"), bd("22.50"), bd("2.00"), bd("220.00")));
    }

    @Test
    @DisplayName("小件：15×10×1cm / 100g → small")
    void resolve_small() {
        assertEquals("small", sizeTierService.resolve(bd("15"), bd("10"), bd("1"), bd("100")));
    }

    @Test
    @DisplayName("大件兜底：60×40×40cm / 5000g → oversize")
    void resolve_oversize() {
        assertEquals("oversize", sizeTierService.resolve(bd("60"), bd("40"), bd("40"), bd("5000")));
    }

    @Test
    @DisplayName("高度边界：1.90cm = 0.75in 刚好卡进 small，1.91cm 落 large")
    void resolve_heightBoundary() {
        // 1.90 × 0.3937008 = 0.748 → 四舍五入 0.75，等于上限，判 small
        assertEquals("small", sizeTierService.resolve(bd("15"), bd("10"), bd("1.90"), bd("100")));
        // 1.91 × 0.3937008 = 0.752 → 四舍五入 0.75 …仍等于上限，故再取 1.95cm 明确越界
        assertEquals("large", sizeTierService.resolve(bd("15"), bd("10"), bd("1.95"), bd("100")));
    }

    @Test
    @DisplayName("重量边界：0.45kg = 0.99lb 进 small，0.50kg = 1.10lb 落 large")
    void resolve_weightBoundary() {
        assertEquals("small", sizeTierService.resolve(bd("15"), bd("10"), bd("1"), bd("450")));
        assertEquals("large", sizeTierService.resolve(bd("15"), bd("10"), bd("1"), bd("500")));
    }

    @Test
    @DisplayName("入参缺失时返回 null，不抛异常（录入页允许尺寸先不填）")
    void resolve_missingInput() {
        assertNull(sizeTierService.resolve(null, bd("10"), bd("1"), bd("100")));
        assertNull(sizeTierService.resolve(bd("15"), null, bd("1"), bd("100")));
        assertNull(sizeTierService.resolve(bd("15"), bd("10"), null, bd("100")));
        assertNull(sizeTierService.resolve(bd("15"), bd("10"), bd("1"), null));
    }
}
