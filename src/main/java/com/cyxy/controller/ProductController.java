package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.exception.CYXYException;
import com.cyxy.model.dto.ProductQueryDTO;
import com.cyxy.model.entity.Product;
import com.cyxy.model.vo.ProductImportResultVO;
import com.cyxy.service.ProductImportService;
import com.cyxy.service.ProductService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年11月02日, 22:43:16
 */

@RestController
@RequestMapping("/api/product")     // 路径必须带 /api，Vite 代理没有 rewrite
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImportService productImportService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(ProductQueryDTO queryParam) {
        return Result.ok(productService.productList(queryParam));
    }

    @GetMapping("/detail")
    public Result<Product> detail(@RequestParam Long id) {
        return Result.ok(productService.showDetails(id));
    }

    @PostMapping("/create")
    public Result<Product> create(@RequestBody Product product) {
        return Result.ok(productService.createProduct(product));
    }

    @PostMapping("/update")
    public Result<Product> update(@RequestBody Product product) {
        return Result.ok(productService.updateProduct(product));
    }

    /**
     * 确认送达：服务端按北京时间取当前时间，与预计送达日期比对，判定提前/准时/晚送达。
     * 请求体只需要 { "id": 1 }，时间不接受前端传值
     */
    @PostMapping("/confirm-delivery")
    public Result<Product> confirmDelivery(@RequestBody Map<String, Object> body) {
        return Result.ok(productService.confirmDelivery(toId(body)));
    }

    /** 撤销确认送达：清空实际送达时间，状态退回配送中，预计送达时间重新可改 */
    @PostMapping("/cancel-delivery")
    public Result<Product> cancelDelivery(@RequestBody Map<String, Object> body) {
        return Result.ok(productService.cancelDelivery(toId(body)));
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        if (id == null) throw new CYXYException("缺少产品 ID");
        productService.removeProduct(Long.valueOf(id.toString()));
        return Result.ok();
    }

    /** 从请求体里取 id，兼容数字与字符串两种传法 */
    private Long toId(Map<String, Object> body) {
        Object id = body == null ? null : body.get("id");
        if (id == null) throw new CYXYException("缺少产品 ID");
        return Long.valueOf(id.toString());
    }

    /* ------------------------------------------------------------------ */
    /* 批量导入                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 下载批量导入模板。
     * 直接写文件流，不套 Result —— 套了就会被 Jackson 序列化成 JSON，浏览器拿不到文件
     */
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        byte[] file = productImportService.buildTemplate();
        String encoded = URLEncoder.encode(ProductImportService.TEMPLATE_FILE_NAME, StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        // filename*=UTF-8''xxx：中文文件名在各浏览器都能保住不乱码
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setContentLength(file.length);
        response.getOutputStream().write(file);
        response.getOutputStream().flush();
    }

    /**
     * 上传 Excel 导入产品。
     * 整批成功或整批失败：任一行不合规，全线不入库，错误明细按「行号 + 列名 + 原因」返回
     */
    @PostMapping("/import")
    public Result<ProductImportResultVO> importProducts(@RequestParam("file") MultipartFile file) {
        return Result.ok(productImportService.importProducts(file));
    }
}