package com.cyxy;

import com.cyxy.model.entity.Product;
import com.cyxy.model.vo.DictOptionVO;
import com.cyxy.model.vo.ProductImportErrorVO;
import com.cyxy.model.vo.ProductImportResultVO;
import com.cyxy.service.DictService;
import com.cyxy.service.ProductImportService;
import com.cyxy.service.ProductService;
import com.cyxy.mapper.ProductMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 产品批量导入测试。
 *
 * 全程 @Transactional，测试结束回滚，不在库里留数据。
 * 覆盖：模板结构、导入成功、必填缺失、类目非法、编号重复（库内 / 同批）、整批回滚。
 */
@SpringBootTest
@Transactional
@DisplayName("产品批量导入")
class ProductImportTest {

    @Autowired
    private ProductImportService importService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private DictService dictService;

    private static final String SHEET_DATA = ProductImportService.SHEET_DATA;

    /* ------------------------------------------------------------------ */
    /* 模板                                                                */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("模板：含数据页与说明页，必填列带 *，四列有下拉")
    void templateStructure() throws Exception {
        byte[] file = importService.buildTemplate();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            Sheet data = wb.getSheet(SHEET_DATA);
            assertNotNull(data, "应有「产品数据」sheet");
            assertNotNull(wb.getSheet("填写说明"), "应有「填写说明」sheet");
            assertTrue(wb.isSheetHidden(wb.getSheetIndex("_选项")), "选项表应隐藏");

            Row header = data.getRow(0);
            // 2026-11-10 起模板多了一列「配送总费用(￥)」，紧跟在「商品总数」后面：
            // 这是自己选择承运人模式专用的人民币费用列，与美元那列按配送方式二选一。
            // 列数从 30 变 31，最后一列「备注」的索引也跟着从 29 挪到 30。
            assertEquals(31, header.getLastCellNum(), "共 31 列");
            assertEquals("*产品编号", header.getCell(0).getStringCellValue());
            assertEquals("*产品名称(中文)", header.getCell(1).getStringCellValue());
            assertEquals("商品总数", header.getCell(23).getStringCellValue());
            assertEquals("配送总费用(￥)", header.getCell(24).getStringCellValue(), "新增列紧跟商品总数");
            assertEquals("备注", header.getCell(30).getStringCellValue());

            // 类目 / 状态 / 头程 / 配送 四个下拉
            assertEquals(4, data.getDataValidations().size());
        }
    }

    /* ------------------------------------------------------------------ */
    /* 导入成功                                                            */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("导入成功：合法数据入库，类目名转成编码，日期被解析")
    void importOk() throws Exception {
        String no = "IMP-" + System.currentTimeMillis();
        Map<String, String> row = validRow(no);
        row.put("预计送达时间", "2026-12-01");

        ProductImportResultVO result = importService.importProducts(file(List.of(row)));

        assertEquals(1, result.total());
        assertEquals(1, result.successCount());
        assertEquals(0, result.failCount());

        Product saved = productMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .eq(Product::getProductNo, no));
        assertNotNull(saved, "应已入库");
        assertEquals("导入测试品", saved.getNameCn());
        assertEquals("DRAFT", saved.getStatus(), "「待评估」应转成 DRAFT");
        assertEquals(LocalDate.of(2026, 12, 1), saved.getExpectedDeliveryDate());
        // 尺寸分段由后端按长宽高重算，导入时不用填
        assertNotNull(saved.getSizeTierCode(), "尺寸分段应自动算出");
    }

    @Test
    @DisplayName("导入成功：一次多条")
    void importMany() throws Exception {
        long ts = System.currentTimeMillis();
        List<Map<String, String>> rows = List.of(
                validRow("IMP-A-" + ts),
                validRow("IMP-B-" + ts),
                validRow("IMP-C-" + ts));

        ProductImportResultVO result = importService.importProducts(file(rows));
        assertEquals(3, result.successCount());
        assertTrue(result.errors().isEmpty());
    }

    /* ------------------------------------------------------------------ */
    /* 校验失败                                                            */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("必填为空：报出行号与列名，整批不入库")
    void missingRequired() throws Exception {
        String no = "IMP-MISS-" + System.currentTimeMillis();
        Map<String, String> row = validRow(no);
        row.put("产品名称(中文)", "");

        ProductImportResultVO result = importService.importProducts(file(List.of(row)));

        assertEquals(1, result.total());
        assertEquals(0, result.successCount());
        assertEquals(1, result.failCount());
        ProductImportErrorVO err = result.errors().get(0);
        assertEquals(2, err.rowNum(), "错误行号应指向 Excel 第 2 行");
        assertEquals(no, err.productNo());
        assertEquals("产品名称(中文)", err.column());
        assertTrue(err.message().contains("不能为空"));

        assertNull(productMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .eq(Product::getProductNo, no)), "整批失败，不该入库");
    }

    @Test
    @DisplayName("类目不存在：提示请从下拉选择")
    void badCategory() throws Exception {
        Map<String, String> row = validRow("IMP-CAT-" + System.currentTimeMillis());
        row.put("产品类目", "不存在的类目");

        ProductImportResultVO result = importService.importProducts(file(List.of(row)));

        assertEquals(1, result.failCount());
        assertEquals("产品类目", result.errors().get(0).column());
        assertTrue(result.errors().get(0).message().contains("下拉"));
    }

    @Test
    @DisplayName("数字格式不对：提示列名与原因")
    void badNumber() throws Exception {
        Map<String, String> row = validRow("IMP-NUM-" + System.currentTimeMillis());
        row.put("采购单价￥", "十二块");

        ProductImportResultVO result = importService.importProducts(file(List.of(row)));

        assertEquals(1, result.failCount());
        assertEquals("采购单价￥", result.errors().get(0).column());
        assertTrue(result.errors().get(0).message().contains("数字"));
    }

    @Test
    @DisplayName("编号已存在：算失败，不覆盖已有产品")
    void duplicateWithDb() throws Exception {
        String no = "IMP-DUP-" + System.currentTimeMillis();
        // 库里 product 有一批 NOT NULL 且无默认值的列，直接 new Product() 插会撞约束，必须补齐
        Product existing = new Product();
        existing.setProductNo(no);
        existing.setNameCn("早就有了");
        existing.setStatus("DRAFT");
        existing.setMainImage("");
        existing.setSourceUrl("https://detail.1688.com/offer/old.html");
        existing.setPurchasePrice(new java.math.BigDecimal("9.90"));
        existing.setAvgDomesticShipping(java.math.BigDecimal.ZERO);
        existing.setNetWeightG(new java.math.BigDecimal("100"));
        existing.setPackagedWeightG(new java.math.BigDecimal("120"));
        existing.setLengthCm(new java.math.BigDecimal("10"));
        existing.setWidthCm(new java.math.BigDecimal("10"));
        existing.setHeightCm(new java.math.BigDecimal("1"));
        productService.createProduct(existing);

        Map<String, String> row = validRow(no);
        row.put("产品名称(中文)", "想覆盖它的新产品");

        ProductImportResultVO result = importService.importProducts(file(List.of(row)));

        assertEquals(1, result.failCount());
        assertTrue(result.errors().get(0).message().contains("已存在"));
        assertEquals(2, result.errors().get(0).rowNum(), "行号要能指回 Excel 第 2 行，不能是 0");

        Product after = productMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .eq(Product::getProductNo, no));
        assertEquals("早就有了", after.getNameCn(), "原有产品不能被导入改掉");
    }

    @Test
    @DisplayName("同批内编号重复：第二条报错")
    void duplicateInFile() throws Exception {
        String no = "IMP-SAME-" + System.currentTimeMillis();
        List<Map<String, String>> rows = List.of(validRow(no), validRow(no));

        ProductImportResultVO result = importService.importProducts(file(rows));

        assertEquals(0, result.successCount());
        assertEquals(1, result.failCount());
        assertTrue(result.errors().get(0).message().contains("重复"));
    }

    @Test
    @DisplayName("整批回滚：三条里一条坏，好的也不入库")
    void allOrNothing() throws Exception {
        long ts = System.currentTimeMillis();
        Map<String, String> bad = validRow("IMP-MIX-BAD-" + ts);
        bad.put("净重g", "");

        List<Map<String, String>> rows = List.of(
                validRow("IMP-MIX-1-" + ts),
                bad,
                validRow("IMP-MIX-2-" + ts));

        ProductImportResultVO result = importService.importProducts(file(rows));

        assertEquals(3, result.total());
        assertEquals(0, result.successCount());
        assertEquals(1, result.failCount());

        for (String no : List.of("IMP-MIX-1-" + ts, "IMP-MIX-2-" + ts)) {
            assertNull(productMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                            .eq(Product::getProductNo, no)), "整批失败，正常行也不能入库");
        }
    }

    @Test
    @DisplayName("空行跳过：不报必填错误")
    void skipBlankRows() throws Exception {
        long ts = System.currentTimeMillis();
        List<Map<String, String>> rows = List.of(
                validRow("IMP-BLK-" + ts),
                new LinkedHashMap<>());   // 空行

        ProductImportResultVO result = importService.importProducts(file(rows));
        assertEquals(1, result.total(), "空行不计入总数");
        assertEquals(1, result.successCount());
    }

    /* ------------------------------------------------------------------ */
    /* 文件层面                                                            */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("非 Excel 文件：直接报错")
    void rejectBadFile() {
        MockMultipartFile bad = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        assertThrows(com.cyxy.exception.CYXYException.class, () -> importService.importProducts(bad));
    }

    @Test
    @DisplayName("缺必填列：提示缺少哪些列")
    void missingColumn() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(SHEET_DATA);
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("产品编号");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            MockMultipartFile file = new MockMultipartFile("file", "t.xlsx", "", out.toByteArray());
            com.cyxy.exception.CYXYException ex = assertThrows(
                    com.cyxy.exception.CYXYException.class, () -> importService.importProducts(file));
            assertTrue(ex.getMessage().contains("缺少"));
        }
    }

    /* ------------------------------------------------------------------ */
    /* 辅助                                                                */
    /* ------------------------------------------------------------------ */

    /** 一行合法数据（12 项必填齐全） */
    private Map<String, String> validRow(String productNo) {
        String category = dictService.getCategoryOptions().stream()
                .findFirst().map(DictOptionVO::name).orElseThrow();
        Map<String, String> row = new LinkedHashMap<>();
        row.put("产品编号", productNo);
        row.put("产品名称(中文)", "导入测试品");
        row.put("产品类目", category);
        row.put("产品状态", "待评估");
        row.put("1688采购链接", "https://detail.1688.com/offer/1.html");
        row.put("采购单价￥", "12.50");
        row.put("平均国内运费￥", "0.50");
        row.put("净重g", "220");
        row.put("含包装实重g", "260");
        row.put("长cm", "24");
        row.put("宽cm", "22.5");
        row.put("高cm", "2");
        return row;
    }

    /** 用真实模板铺一行数据，模拟用户填好的文件 */
    private MockMultipartFile file(List<Map<String, String>> rows) throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(importService.buildTemplate()))) {
            Sheet sheet = wb.getSheet(SHEET_DATA);
            Row header = sheet.getRow(0);
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String raw = header.getCell(i).getStringCellValue();
                idx.put(raw.replace("*", "").trim(), i);
            }
            int r = 1;
            for (Map<String, String> data : rows) {
                Row row = sheet.createRow(r++);
                data.forEach((k, v) -> {
                    Integer i = idx.get(k);
                    if (i != null && v != null && !v.isEmpty()) {
                        row.createCell(i).setCellValue(v);
                    }
                });
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            bytes = out.toByteArray();
        }
        return new MockMultipartFile("file", "products.xlsx", "", bytes);
    }

    /** 顺带确认 result 的 fail 语义：失败时 successCount 必为 0 */
    @Test
    @DisplayName("结果语义：失败时成功数为 0")
    void failMeansZeroSuccess() throws Exception {
        Map<String, String> row = validRow("IMP-Z-" + System.currentTimeMillis());
        row.put("产品状态", "乱写的状态");

        ProductImportResultVO result = importService.importProducts(file(List.of(row)));
        assertEquals(0, result.successCount());
        assertFalse(result.errors().isEmpty());
        assertEquals(result.errors().size(), result.failCount());
    }
}
