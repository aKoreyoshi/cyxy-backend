package com.cyxy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.common.Result;
import com.cyxy.mapper.ProductMapper;
import com.cyxy.model.dto.ProfitCalcFormDTO;
import com.cyxy.model.entity.Product;
import com.cyxy.model.entity.ProfitCalcResult;
import com.cyxy.model.vo.DictOptionVO;
import com.cyxy.service.ProfitCalcService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author: Koreyoshi
 * @description: FBA 利润计算器接口
 * 四个接口对应前端计算器页的全部交互：
 * - calc  试算（不落库，页面「计算」按钮）
 * - save  保存（事务落库，页面「保存」按钮；选中产品才联动 product_fba_fee）
 * - product-options  产品远程搜索下拉（显示「编号 中文名」，value 为 product.id）
 * - product-prefill  选中产品后带出已有信息
 * @version: 1.0
 * @date: 2026年11月04日, 00:00:00
 */
@RestController
@RequestMapping("/api/profit")     // 必须带 /api 前缀，Vite 代理无 rewrite
@RequiredArgsConstructor
public class ProfitController {

    /** 产品下拉一次最多返回的条数 */
    private static final long OPTION_LIMIT = 20;

    private final ProfitCalcService profitCalcService;
    private final ProductMapper productMapper;

    /** 试算：只算不存 */
    @PostMapping("/calc")
    public Result<ProfitCalcResult> calc(@RequestBody ProfitCalcFormDTO form) {
        return Result.ok(profitCalcService.calculate(form));
    }

    /** 保存：写方案 + 结果快照；选中产品时同步 upsert 产品 FBA 费用 */
    @PostMapping("/save")
    public Result<ProfitCalcResult> save(@RequestBody ProfitCalcFormDTO form) {
        return Result.ok(profitCalcService.save(form));
    }

    /** 产品下拉选项：关键字匹配中文名或编号，显示「编号 中文名」 */
    @GetMapping("/product-options")
    public Result<List<DictOptionVO>> productOptions(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .orderByDesc(Product::getId)
                .last("limit " + OPTION_LIMIT);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Product::getNameCn, keyword)
                    .or()
                    .like(Product::getProductNo, keyword));
        }
        List<Product> products = productMapper.selectList(wrapper);
        return Result.ok(products.stream()
                .map(p -> new DictOptionVO(String.valueOf(p.getId()),
                        p.getProductNo() + " " + p.getNameCn()))
                .toList());
    }

    /** 选中产品后带出：尺寸 / 含包装实重 / 采购成本 / 售价 / 退货类目 / 头程 / 汇率 / 上次手填费用 */
    @GetMapping("/product-prefill")
    public Result<ProfitCalcFormDTO> prefill(@RequestParam Long productId) {
        return Result.ok(profitCalcService.prefill(productId));
    }
}
