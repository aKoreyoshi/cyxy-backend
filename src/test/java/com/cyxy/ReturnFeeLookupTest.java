package com.cyxy;

import com.cyxy.model.vo.ReturnFeeLookupVO;
import com.cyxy.service.ProfitDictLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 尾程退货处理费「按尺寸分段 + 计费重自动带出」的口径锁定。
 *
 * 基准值来源：D:/Workbuddy/尾程退货费.xlsx（Sheet1，R4~R29），
 * 已按 1 lb = 16 oz 换算。分组口径由用户给定（7 组），
 * 50-70 / 70-150 两行的匹配边界按分组名修正，A 列原文只作展示。
 * 这些断言保护四件容易出错的事：
 * ① 区间左开右闭；② 重叠区间按表中顺序取第一档；
 * ③ FORMULA 档续重向上取整；④ 大件重量链 0-50 / 50-70 / 70-150 / >150 首尾相接。
 */
@SpringBootTest
class ReturnFeeLookupTest {

    @Autowired private ProfitDictLoader loader;

    /** 计费重以 lb 传入，返回查表得到的费用 */
    private BigDecimal fee(String tier, String lb) {
        return loader.lookupReturnFee(tier, new BigDecimal(lb)).feeUsd();
    }

    private void assertFee(String expected, String actualLb, String tier) {
        assertEquals(0, new BigDecimal(expected).compareTo(fee(tier, actualLb)),
                tier + " / " + actualLb + " lb 应带出 $" + expected);
    }

    @Test
    @DisplayName("小号标准 8 档：2oz 步长，1.78 ~ 2.21")
    void smallStandardBrackets() {
        assertFee("1.78", "0.0625", "small");   // 1 oz
        assertFee("1.84", "0.1875", "small");   // 3 oz
        assertFee("1.90", "0.3125", "small");   // 5 oz
        assertFee("2.21", "0.9375", "small");   // 15 oz
    }

    @Test
    @DisplayName("大号标准固定 12 档：与 dict_fba_rate 的档位边界一致")
    void largeStandardBrackets() {
        assertFee("2.36", "0.125", "large");    // 2 oz → 不超过 4 盎司
        assertFee("2.70", "0.375", "large");    // 6 oz → 4~8 盎司
        assertFee("3.05", "0.625", "large");    // 10 oz → 8~12 盎司
        assertFee("3.39", "0.875", "large");    // 14 oz → 12~16 盎司
        assertFee("3.70", "1.125", "large");    // 18 oz → 1~1.25 磅
        assertFee("4.73", "2.9", "large");      // 46.4 oz → 2.75~3 磅
    }

    @Test
    @DisplayName("区间左开右闭：正好 16oz 落在上一档")
    void boundaryIsLeftOpenRightClosed() {
        assertFee("3.39", "1", "large");        // 16 oz 仍属 12~16 盎司档
        assertFee("3.70", "1.01", "large");     // 超过 16 oz 才进下一档
    }

    @Test
    @DisplayName("FORMULA 档：起步价 + 超出部分按步进向上取整")
    void formulaBrackets() {
        // large 3~20lb：5.00 + ceil((56-48)/4) × 0.05 = 5.10
        assertFee("5.10", "3.5", "large");
        // 不足一个 4oz 步进也算一个：48.1oz → 只加一次 0.05
        assertFee("5.05", "3.00625", "large");
        // oversize 10lb 命中重叠区间里 sort_no 最小的第一档：
        // 6.74 + ceil((160-16)/16) × 0.32 = 9.62
        assertFee("9.62", "10", "oversize");
    }

    @Test
    @DisplayName("大件重量链：0-50 / 50-70 / 70-150 / >150 磅首尾相接，边界按分组名")
    void oversizeWeightChain() {
        // 0~50lb 有两行重叠（大号大件 / 超大尺寸），按表中顺序取「大号大件」
        // 50lb = 800oz：6.74 + ceil((800-16)/16)×0.32 = 22.42
        assertFee("22.42", "50", "oversize");
        // 超过 50lb 才进「超大件：50-70磅」；此时还没到 51lb 起算点，只收起步价
        assertFee("40.12", "50.1", "oversize");
        // 60lb = 960oz：40.12 + ceil((960-816)/16)×0.75 = 46.87
        assertFee("46.87", "60", "oversize");
        // 70lb 仍在上一档（右闭）；再重一点点进「超大件：70-150磅」
        assertFee("54.37", "70", "oversize");
        assertFee("52.45", "70.1", "oversize");
        // 100lb = 1600oz：52.45 + ceil((1600-1136)/16)×0.75 = 74.20
        assertFee("74.20", "100", "oversize");
        // 超过 150lb 进最后一档
        assertFee("157.35", "151", "oversize");
    }

    @Test
    @DisplayName("不传大类时纯按计费重匹配：候选按原表顺序，重叠取最前一档")
    void weightOnlyLookup() {
        ReturnFeeLookupVO vo = loader.lookupReturnFee(null, new BigDecimal("0.375"));
        assertTrue(vo.matched());
        // 6oz 同时落在小号 4~6oz 与大号 4~8oz，按表中顺序取小号标准
        assertEquals("SMALL_STANDARD", vo.groupCode());
        assertEquals("小号标准尺寸", vo.groupName());
        assertEquals(0, new BigDecimal("1.90").compareTo(vo.feeUsd()));
    }

    @Test
    @DisplayName("计费重为 0 兜底命中首档；未命中时费用留空让前端保留手填值")
    void edgeCases() {
        assertFee("1.78", "0", "small");
        assertFee("2.36", "0", "large");

        ReturnFeeLookupVO missed = loader.lookupReturnFee("unknown", new BigDecimal("1"));
        assertFalse(missed.matched(), "未知分段应标记未命中");
        assertNull(missed.feeUsd(), "未命中不能写 0，否则会覆盖用户手填值");

        ReturnFeeLookupVO noWeight = loader.lookupReturnFee("small", null);
        assertFalse(noWeight.matched(), "计费重缺失应标记未命中");
    }

    @Test
    @DisplayName("返回值带上命中区间原文，供页面说明「为什么是这个价」")
    void lookupCarriesMatchedLabel() {
        ReturnFeeLookupVO vo = loader.lookupReturnFee("large", new BigDecimal("0.875"));
        assertTrue(vo.matched());
        assertEquals("FLAT", vo.feeType());
        assertEquals("LARGE_STANDARD", vo.groupCode());
        assertEquals("大号标准尺寸", vo.groupName());
        assertEquals("12 至 16 盎司（不含 12 盎司）", vo.weightLabel());
        assertEquals(0, new BigDecimal("14.0000").compareTo(vo.billWeightOz()));
    }

    @Test
    @DisplayName("利润回归基准的计费重 0.485lb 应带出 2.70，公式本身不受影响")
    void regressionBaselineWeight() {
        // 24×22.5×2cm / 0.22kg 时计费重约 0.485lb（7.76oz），落在 large 4~8oz 档。
        // 注意：回归基准本身是「传入 3.39 得到总成本 95.13 / 利润 31.72」，
        // 这里只验证自动带出能算出对应档位，不去改 calc.js 的公式。
        assertFee("2.70", "0.485", "large");
    }
}
