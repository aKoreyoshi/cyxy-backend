package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.model.entity.DictExchangeRate;
import com.cyxy.model.vo.CommissionTreeNodeVO;
import com.cyxy.model.vo.DictOptionVO;
import com.cyxy.service.DictDisplayService;
import com.cyxy.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典接口。
 *
 * <p>分两类，别混用：</p>
 * <ul>
 *   <li>/category —— 产品录入下拉专用，只返回 code/name（DictService，带缓存 + 有效期过滤）；</li>
 *   <li>/table、/commission-tree、/exchange-rate —— 「参数列表」展示页专用，
 *       返回原始行数据，不过滤停用/过期（DictDisplayService）。</li>
 * </ul>
 *
 * @author: Koreyoshi
 */
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;
    private final DictDisplayService dictDisplayService;

    /**
     * 产品类目下拉
     * GET /api/dict/category → [{ code, name }]
     */
    @GetMapping("/category")
    public Result<List<DictOptionVO>> category() {
        return Result.ok(dictService.getCategoryOptions());
    }

    /**
     * 通用二维字典表
     * GET /api/dict/table?type=return-threshold
     * type 白名单：return-threshold / size-tier / fba-rate / first-leg / step-rate / sys-config
     */
    @GetMapping("/table")
    public Result<?> table(@RequestParam String type) {
        return Result.ok(dictDisplayService.table(type));
    }

    /** 佣金费率树：类目（父）+ 阶梯档位（子） */
    @GetMapping("/commission-tree")
    public Result<List<CommissionTreeNodeVO>> commissionTree() {
        return Result.ok(dictDisplayService.commissionTree());
    }

    /** 汇率时序，按日期升序；表格与折线图共用同一份数据 */
    @GetMapping("/exchange-rate")
    public Result<List<DictExchangeRate>> exchangeRate() {
        return Result.ok(dictDisplayService.exchangeRates());
    }
}
