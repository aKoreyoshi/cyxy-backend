package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.DictFirstLegMethodMapper;
import com.cyxy.mapper.ProductMapper;
import com.cyxy.model.entity.DictFirstLegMethod;
import com.cyxy.model.entity.Product;
import com.cyxy.model.vo.DictOptionVO;
import com.cyxy.model.vo.ProductImportErrorVO;
import com.cyxy.model.vo.ProductImportResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产品批量导入：模板下载 + Excel 解析入库。
 *
 * <h3>两条硬规则（用户 2026-11-03 拍板）</h3>
 * <ol>
 *   <li><b>整批成功或整批失败</b>：任一行不合规，全批都不入库。
 *       避免出现「导了一半」这种说不清库里到底有什么的状态。</li>
 *   <li><b>编号已存在算失败，不覆盖</b>：已有的产品不会被导入悄悄改掉。</li>
 * </ol>
 *
 * <h3>图片</h3>
 * 模板里没有图片列（Excel 里塞图片靠锚点定位，行一动就贴错产品，太脆）。
 * 导入的产品主图留空，等对象存储接好后走「按产品编号匹配文件名」的批量补图，
 * 模板结构不变。因此 <b>导入的主图不参与必填校验</b>（手工录入那 13 项里唯一豁免的一项）。
 *
 * @version: 1.0
 * @date: 2026年11月03日
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final ProductMapper productMapper;
    private final ProductService productService;
    private final DictService dictService;
    private final DictFirstLegMethodMapper firstLegMethodMapper;

    /** 数据 sheet 名：导入时优先按这个名字找，找不到才退回第一个 sheet */
    public static final String SHEET_DATA = "产品数据";
    /** 选项 sheet：隐藏，作为下拉的数据源 */
    private static final String SHEET_OPTIONS = "_选项";
    private static final String SHEET_GUIDE = "填写说明";

    public static final String TEMPLATE_FILE_NAME = "产品批量导入模板.xlsx";

    /** 下拉生效的行数：覆盖到 500 行，一次批量导入足够了 */
    private static final int DROPDOWN_ROWS = 500;

    /**
     * 列定义：模板表头与导入解析共用同一份，改字段只改这里。
     *
     * @param header   中文表头（必填在生成模板时自动加 *）
     * @param field    Product 的字段名（camelCase）
     * @param required 是否必填
     * @param type     目标类型
     * @param dict     下拉来源 key（category / status / firstLeg / shipping），null 表示自由填写
     * @param tip      填写说明 sheet 里的说明文字
     */
    private record ColDef(String header, String field, boolean required, Class<?> type,
                          String dict, String tip) {
    }

    private static final List<ColDef> COLUMNS = List.of(
            new ColDef("产品编号", "productNo", true, String.class, null, "唯一标识，不能与已有产品重复"),
            new ColDef("产品名称(中文)", "nameCn", true, String.class, null, ""),
            new ColDef("产品名称(英文)", "nameEn", false, String.class, null, ""),
            new ColDef("产品类目", "categoryCode", true, String.class, "category", "从下拉里选；决定退货阈值等参数"),
            new ColDef("产品状态", "status", true, String.class, "status", "待评估 / 已选 / 已下架"),
            new ColDef("竞品ASIN", "competitorAsin", false, String.class, null, "亚马逊竞品 ASIN，选填"),
            new ColDef("1688采购链接", "sourceUrl", true, String.class, null, "必须是 http/https 开头的完整链接"),
            new ColDef("供应商", "supplier", false, String.class, null, ""),
            new ColDef("采购单价￥", "purchasePrice", true, BigDecimal.class, null, "单件采购价，单位元"),
            new ColDef("起订量", "moq", false, Integer.class, null, "整数，件"),
            new ColDef("包材贴标￥", "packageCost", false, BigDecimal.class, null, "单件包材+贴标费用"),
            new ColDef("平均国内运费￥", "avgDomesticShipping", true, BigDecimal.class, null, "单件分摊的国内运费，可以为 0"),
            new ColDef("关税费￥/件", "tariffFee", false, BigDecimal.class, null, "不填则后续按全局关税率计算"),
            new ColDef("净重g", "netWeightG", true, BigDecimal.class, null, "产品本身重量，单位克"),
            new ColDef("含包装实重g", "packagedWeightG", true, BigDecimal.class, null, "带包装称重，单位克"),
            new ColDef("长cm", "lengthCm", true, BigDecimal.class, null, "包装外径，单位厘米"),
            new ColDef("宽cm", "widthCm", true, BigDecimal.class, null, "包装外径，单位厘米"),
            new ColDef("高cm", "heightCm", true, BigDecimal.class, null, "包装外径，单位厘米；尺寸分段由后端按这三项自动算，不用填"),
            new ColDef("头程方式", "firstLegMethodCode", false, String.class, "firstLeg", "从下拉里选"),
            new ColDef("配送方式", "shippingMethod", false, String.class, "shipping", "亚马逊SEND 填配送总费用；自选承运人填运费单价"),
            new ColDef("物流商", "logisticsProvider", false, String.class, null, ""),
            new ColDef("头程运费单价￥/kg", "firstLegUnitPrice", false, BigDecimal.class, null, "自选承运人时必填"),
            new ColDef("配送总费用$", "shippingTotalFeeUsd", false, BigDecimal.class, null, "亚马逊SEND 的整票费用，单位美元"),
            new ColDef("商品总数", "totalUnits", false, Integer.class, null, "本票货件总件数，整数"),
            new ColDef("预计送达时间", "expectedDeliveryDate", false, LocalDate.class, null, "格式 2026-11-03；填了就是「配送中」，到货后可在系统里确认送达"),
            new ColDef("定价$", "listPriceUsd", false, BigDecimal.class, null, "售价，单位美元；不填按 0 处理"),
            new ColDef("竞品售价$", "competitorPriceUsd", false, BigDecimal.class, null, ""),
            new ColDef("预估月销", "monthlySalesEst", false, Integer.class, null, "整数，件"),
            new ColDef("首批备货量", "firstBatchQty", false, Integer.class, null, "整数，件"),
            new ColDef("备注", "remark", false, String.class, null, "")
    );

    /* ------------------------------------------------------------------ */
    /* 一、模板下载                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * 生成导入模板：数据 sheet（表头 + 下拉校验）+ 填写说明 sheet + 隐藏的选项 sheet。
     *
     * @return xlsx 文件字节
     */
    public byte[] buildTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Map<String, List<String>> options = loadOptions();
            Sheet optionSheet = buildOptionSheet(wb, options);
            buildDataSheet(wb, options);
            buildGuideSheet(wb);
            // 选项表只是给下拉当数据源，用户不需要看见
            wb.setSheetHidden(wb.getSheetIndex(optionSheet), true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 下拉选项：key 与 ColDef.dict 对应 */
    private Map<String, List<String>> loadOptions() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("category", dictService.getCategoryOptions().stream()
                .map(DictOptionVO::name).filter(Objects::nonNull).toList());
        map.put("status", List.of("待评估", "已选", "已下架"));
        map.put("firstLeg", firstLegMethodMapper.selectList(
                        new LambdaQueryWrapper<DictFirstLegMethod>()
                                .eq(DictFirstLegMethod::getStatus, 1)
                                .orderByAsc(DictFirstLegMethod::getSortNo))
                .stream().map(DictFirstLegMethod::getMethodName).filter(Objects::nonNull).toList());
        map.put("shipping", List.of("亚马逊SEND", "自己选择承运人"));
        return map;
    }

    /** 隐藏的选项 sheet：每列一组选项，第 1 行是组名，数据从第 2 行起（与下拉公式范围对应） */
    private Sheet buildOptionSheet(XSSFWorkbook wb, Map<String, List<String>> options) {
        Sheet sheet = wb.createSheet(SHEET_OPTIONS);
        int col = 0;
        for (Map.Entry<String, List<String>> entry : options.entrySet()) {
            Row head = sheet.createRow(0);
            head.createCell(col).setCellValue(entry.getKey());
            List<String> values = entry.getValue();
            for (int i = 0; i < values.size(); i++) {
                Row row = sheet.getRow(i + 1);
                if (row == null) row = sheet.createRow(i + 1);
                row.createCell(col).setCellValue(values.get(i));
            }
            col++;
        }
        return sheet;
    }

    private void buildDataSheet(XSSFWorkbook wb, Map<String, List<String>> options) {
        XSSFSheet sheet = wb.createSheet(SHEET_DATA);

        CellStyle headStyle = wb.createCellStyle();
        Font headFont = wb.createFont();
        headFont.setBold(true);
        headStyle.setFont(headFont);
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        headStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        headStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

        // 日期列：给个 yyyy-mm-dd 格式，用户填的日期显示更直观
        DataFormat dataFormat = wb.createDataFormat();
        CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));

        Row headRow = sheet.createRow(0);
        for (int i = 0; i < COLUMNS.size(); i++) {
            ColDef col = COLUMNS.get(i);
            Cell cell = headRow.createCell(i);
            cell.setCellValue(col.required() ? "*" + col.header() : col.header());
            cell.setCellStyle(headStyle);
            // 列宽按表头长度估算，中文按两个字符宽算
            sheet.setColumnWidth(i, Math.min(30, col.header().length() * 2 + 6) * 256);
            if (col.type() == LocalDate.class) {
                sheet.setDefaultColumnStyle(i, dateStyle);
            }
        }
        // 冻结表头，翻到第 200 行也知道每列是什么
        sheet.createFreezePane(0, 1);

        // 下拉：Excel 的「显式列表」最多 255 字符，33 个类目名肯定超，
        // 所以指向隐藏的选项 sheet 区域，没有长度限制
        for (int i = 0; i < COLUMNS.size(); i++) {
            ColDef col = COLUMNS.get(i);
            if (col.dict() == null) continue;
            List<String> values = options.get(col.dict());
            if (values == null || values.isEmpty()) continue;

            String formula = "'" + SHEET_OPTIONS + "'!$" + columnLetter(i) + "$2:$"
                    + columnLetter(i) + "$" + (values.size() + 1);
            XSSFDataValidationHelper helper = new XSSFDataValidationHelper(sheet);
            XSSFDataValidation validation = (XSSFDataValidation) helper.createValidation(
                    helper.createFormulaListConstraint(formula),
                    new CellRangeAddressList(1, DROPDOWN_ROWS, i, i));
            validation.setSuppressDropDownArrow(false);
            validation.setShowErrorBox(true);
            validation.createErrorBox("取值不在列表中", "「" + col.header() + "」请从下拉列表中选择");
            sheet.addValidationData(validation);
        }
    }

    /** 列索引 → A/B/C...，最多到 ZZ，模板 30 列用不到那么远 */
    private static String columnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private void buildGuideSheet(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet(SHEET_GUIDE);
        CellStyle headStyle = wb.createCellStyle();
        Font headFont = wb.createFont();
        headFont.setBold(true);
        headStyle.setFont(headFont);

        Row head = sheet.createRow(0);
        String[] heads = {"列名", "是否必填", "填写说明"};
        for (int i = 0; i < heads.length; i++) {
            Cell cell = head.createCell(i);
            cell.setCellValue(heads[i]);
            cell.setCellStyle(headStyle);
        }
        for (int i = 0; i < COLUMNS.size(); i++) {
            ColDef col = COLUMNS.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(col.header());
            row.createCell(1).setCellValue(col.required() ? "必填" : "选填");
            row.createCell(2).setCellValue(col.tip() == null ? "" : col.tip());
        }
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 70 * 256);

        // 类目清单：业务同事不一定记得全名，直接抄这里
        int rowNum = COLUMNS.size() + 2;
        Row title = sheet.createRow(rowNum++);
        title.createCell(0).setCellValue("产品类目可选项");
        title.getCell(0).setCellStyle(headStyle);
        List<String> categories = dictService.getCategoryOptions().stream()
                .map(DictOptionVO::name).filter(Objects::nonNull).toList();
        Row values = sheet.createRow(rowNum);
        values.createCell(0).setCellValue(String.join("、", categories));
    }

    /* ------------------------------------------------------------------ */
    /* 二、导入解析                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * 导入产品。
     *
     * <p>先全量校验再入库：任何一行有问题，整个文件都不落库，
     * 并把「行号 + 产品编号 + 出错列 + 原因」全部返回，用户改完重传。</p>
     *
     * @param file 上传的 xlsx / xls
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductImportResultVO importProducts(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CYXYException("请先选择要导入的 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new CYXYException("只支持 .xlsx / .xls 格式的 Excel 文件");
        }

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheet(SHEET_DATA);
            if (sheet == null) {
                // 用户可能删了 sheet 名或另存过，退回第一个 sheet
                sheet = wb.getSheetAt(0);
            }
            return doImport(wb, sheet);
        } catch (CYXYException e) {
            throw e;
        } catch (IOException e) {
            throw new CYXYException("文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            throw new CYXYException("Excel 解析失败，请确认文件没有损坏或加密：" + e.getMessage());
        }
    }

    private ProductImportResultVO doImport(Workbook wb, Sheet sheet) {
        Row headRow = sheet.getRow(sheet.getFirstRowNum());
        if (headRow == null) {
            throw new CYXYException("表格是空的，请先下载模板并按模板填写");
        }

        // 表头 → 列索引。按「表头名」而不是列号定位，业务同事插一列、调个顺序都不会错位
        Map<String, Integer> colIndex = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (int i = headRow.getFirstCellNum(); i < headRow.getLastCellNum(); i++) {
            Cell cell = headRow.getCell(i);
            if (cell == null) continue;
            String raw = formatter.formatCellValue(cell).trim();
            String cleaned = cleanHeader(raw);
            if (!cleaned.isEmpty()) colIndex.put(cleaned, i);
        }

        List<String> missing = COLUMNS.stream()
                .filter(ColDef::required)
                .map(ColDef::header)
                .filter(h -> !colIndex.containsKey(h))
                .toList();
        if (!missing.isEmpty()) {
            throw new CYXYException("表格缺少这些必填列：" + String.join("、", missing)
                    + "。请重新下载模板填写");
        }

        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
        Map<String, String> categoryByName = nameToCodeMap(dictService.getCategoryOptions());
        Map<String, String> statusByName = Map.of("待评估", "DRAFT", "已选", "SELECTED", "已下架", "OFFLINE");
        Map<String, String> firstLegByName = firstLegMethodMapper.selectList(new LambdaQueryWrapper<>())
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getMethodName() == null ? "" : r.getMethodName(),
                        r -> r.getMethodCode() == null ? "" : r.getMethodCode(),
                        (a, b) -> a, HashMap::new));
        Map<String, String> shippingByName = Map.of("亚马逊SEND", "AMAZON_SEND", "自己选择承运人", "SELF_CARRIER");

        List<ProductImportErrorVO> errors = new ArrayList<>();
        List<Product> products = new ArrayList<>();
        // 每行在 Excel 里的行号，与 products 一一对应：
        // 库内重复是解析完全部行之后才查的，得留着行号才能报出「第几行」
        List<Integer> rowNums = new ArrayList<>();
        List<String> productNos = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        int total = 0;

        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isBlankRow(row, formatter, evaluator)) continue;
            total++;
            int rowNum = r + 1;   // Excel 行号从 1 开始，与用户看到的左侧数字一致

            String productNo = readString(row, colIndex.get("产品编号"), formatter, evaluator);
            Product product = new Product();
            product.setProductNo(productNo);
            // 主图没有列（Excel 塞图片太脆），保持空串：库里 NOT NULL，且导入不校验必填
            product.setMainImage("");

            for (ColDef col : COLUMNS) {
                Integer idx = colIndex.get(col.header());
                if (idx == null) continue;
                String raw = readString(row, idx, formatter, evaluator);

                if (raw.isEmpty()) {
                    if (col.required()) {
                        errors.add(new ProductImportErrorVO(rowNum, productNo, col.header(), "不能为空"));
                    }
                    continue;
                }

                // 名称 → 编码：类目、状态、头程、配送四列都是给人看的名字，库里存编码
                String value = raw;
                if ("category".equals(col.dict())) {
                    value = resolveCode(raw, categoryByName, "产品类目");
                } else if ("status".equals(col.dict())) {
                    value = resolveCode(raw, statusByName, "产品状态");
                } else if ("firstLeg".equals(col.dict())) {
                    value = resolveCode(raw, firstLegByName, "头程方式");
                } else if ("shipping".equals(col.dict())) {
                    value = resolveCode(raw, shippingByName, "配送方式");
                }
                if (value == null) {
                    errors.add(new ProductImportErrorVO(rowNum, productNo, col.header(),
                            "「" + raw + "」不是有效的选项，请从下拉列表中选择"));
                    continue;
                }

                try {
                    setTypedValue(product, col, value);
                } catch (IllegalArgumentException e) {
                    errors.add(new ProductImportErrorVO(rowNum, productNo, col.header(),
                            "格式不对：" + raw + "（" + typeHint(col.type()) + "）"));
                }
            }

            // 同文件内编号重复
            if (productNo != null && !productNo.isEmpty()) {
                if (!seenInFile.add(productNo)) {
                    errors.add(new ProductImportErrorVO(rowNum, productNo, "产品编号",
                            "与本次表格中前面的某一行重复"));
                } else {
                    productNos.add(productNo);
                }
            }

            products.add(product);
            rowNums.add(rowNum);
        }

        if (total == 0) {
            throw new CYXYException("没有读到任何数据行，请确认表格里填了产品信息");
        }

        // 与库里已有编号比对：一次查完，别逐行查库
        Set<String> existsInDb = queryExistingNos(productNos);
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            if (p.getProductNo() != null && existsInDb.contains(p.getProductNo())) {
                errors.add(new ProductImportErrorVO(rowNums.get(i), p.getProductNo(), "产品编号",
                        "该编号已存在，导入不会覆盖已有产品"));
            }
        }

        // 整批成功或整批失败：有一行不对，一条都不入库
        if (!errors.isEmpty()) {
            return ProductImportResultVO.failed(total, errors);
        }

        for (Product p : products) {
            productService.createProduct(p);   // 内部会重算尺寸分段、补 list_price_usd
        }
        return ProductImportResultVO.allOk(total);
    }

    /** 批量查已存在的编号（一次查询，避免 N 次往返） */
    private Set<String> queryExistingNos(List<String> nos) {
        if (nos.isEmpty()) return Set.of();
        Set<String> result = new HashSet<>();
        // in 列表过长会撑爆 SQL，按 500 一批
        for (int i = 0; i < nos.size(); i += 500) {
            List<String> batch = nos.subList(i, Math.min(nos.size(), i + 500));
            result.addAll(productMapper.selectList(new LambdaQueryWrapper<Product>()
                            .select(Product::getProductNo)
                            .in(Product::getProductNo, batch))
                    .stream().map(Product::getProductNo).filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        }
        return result;
    }

    /** 名称 → 编码；找不到时按编码再试一次（有人会直接填 DRAFT 这样的编码） */
    private static String resolveCode(String raw, Map<String, String> byName, String label) {
        if (byName.containsKey(raw)) return byName.get(raw);
        String upper = raw.trim().toUpperCase();
        if (byName.containsValue(upper)) return upper;
        return null;
    }

    private static Map<String, String> nameToCodeMap(List<DictOptionVO> options) {
        Map<String, String> map = new HashMap<>();
        options.forEach(o -> {
            if (o.name() != null && o.code() != null) map.put(o.name().trim(), o.code());
        });
        return map;
    }

    /** 表头清洗：去掉必填星号与空格，让「*产品编号」和「产品编号」都能对上 */
    private static String cleanHeader(String raw) {
        return raw.replace("*", "").replace("＊", "").trim();
    }

    private static boolean isBlankRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell == null) continue;
            if (!formatter.formatCellValue(cell, evaluator).trim().isEmpty()) return false;
        }
        return true;
    }

    private static String readString(Row row, Integer idx, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (idx == null) return "";
        Cell cell = row.getCell(idx);
        if (cell == null) return "";
        // 日期单元格：直接取日期值，避免 DataFormatter 输出「2026年11月3日」这种解析不了的串
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private static void setTypedValue(Product product, ColDef col, String raw) {
        Object value;
        if (col.type() == String.class) {
            value = raw;
        } else if (col.type() == BigDecimal.class) {
            value = parseDecimal(raw);
        } else if (col.type() == Integer.class) {
            value = parseDecimal(raw).setScale(0, RoundingMode.HALF_UP).intValue();
        } else if (col.type() == LocalDate.class) {
            value = parseDate(raw);
        } else {
            value = raw;
        }
        setValue(product, col.field(), value);
    }

    /** 数字清洗：允许带 ￥ $ 元 千分位逗号 空格 */
    private static BigDecimal parseDecimal(String raw) {
        String cleaned = raw.replaceAll("[,\\s￥$元%]", "");
        if (cleaned.isEmpty()) throw new IllegalArgumentException("empty");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + raw);
        }
    }

    /** 日期：接受 2026-11-03 / 2026/11/3 / 20261103 */
    private static LocalDate parseDate(String raw) {
        String s = raw.replace("/", "-").replace(".", "-").trim();
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            // 2026-11-3 这种一位数月/日，ISO 格式解析不了，拆开手动拼
            String[] parts = s.split("-");
            if (parts.length == 3) {
                try {
                    return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]));
                } catch (Exception e) {
                    throw new IllegalArgumentException("bad date: " + raw);
                }
            }
            throw new IllegalArgumentException("bad date: " + raw);
        }
    }

    private static String typeHint(Class<?> type) {
        if (type == BigDecimal.class) return "请填数字";
        if (type == Integer.class) return "请填整数";
        if (type == LocalDate.class) return "日期格式如 2026-11-03";
        return "请填文本";
    }

    /** 字段赋值：加新列时这里补一个 case，写着直白但比反射好排查 */
    private static void setValue(Product product, String field, Object value) {
        switch (field) {
            case "productNo" -> product.setProductNo((String) value);
            case "nameCn" -> product.setNameCn((String) value);
            case "nameEn" -> product.setNameEn((String) value);
            case "categoryCode" -> product.setCategoryCode((String) value);
            case "status" -> product.setStatus((String) value);
            case "competitorAsin" -> product.setCompetitorAsin((String) value);
            case "sourceUrl" -> product.setSourceUrl((String) value);
            case "supplier" -> product.setSupplier((String) value);
            case "purchasePrice" -> product.setPurchasePrice((BigDecimal) value);
            case "moq" -> product.setMoq((Integer) value);
            case "packageCost" -> product.setPackageCost((BigDecimal) value);
            case "avgDomesticShipping" -> product.setAvgDomesticShipping((BigDecimal) value);
            case "tariffFee" -> product.setTariffFee((BigDecimal) value);
            case "netWeightG" -> product.setNetWeightG((BigDecimal) value);
            case "packagedWeightG" -> product.setPackagedWeightG((BigDecimal) value);
            case "lengthCm" -> product.setLengthCm((BigDecimal) value);
            case "widthCm" -> product.setWidthCm((BigDecimal) value);
            case "heightCm" -> product.setHeightCm((BigDecimal) value);
            case "firstLegMethodCode" -> product.setFirstLegMethodCode((String) value);
            case "shippingMethod" -> product.setShippingMethod((String) value);
            case "logisticsProvider" -> product.setLogisticsProvider((String) value);
            case "firstLegUnitPrice" -> product.setFirstLegUnitPrice((BigDecimal) value);
            case "shippingTotalFeeUsd" -> product.setShippingTotalFeeUsd((BigDecimal) value);
            case "totalUnits" -> product.setTotalUnits((Integer) value);
            case "expectedDeliveryDate" -> product.setExpectedDeliveryDate((LocalDate) value);
            case "listPriceUsd" -> product.setListPriceUsd((BigDecimal) value);
            case "competitorPriceUsd" -> product.setCompetitorPriceUsd((BigDecimal) value);
            case "monthlySalesEst" -> product.setMonthlySalesEst((Integer) value);
            case "firstBatchQty" -> product.setFirstBatchQty((Integer) value);
            case "remark" -> product.setRemark((String) value);
            default -> throw new IllegalArgumentException("未知字段：" + field);
        }
    }
}
