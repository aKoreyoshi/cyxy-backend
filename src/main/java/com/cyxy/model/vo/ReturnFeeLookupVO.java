package com.cyxy.model.vo;

import java.math.BigDecimal;

/**
 * @author: Koreyoshi
 * @description: 按「计费重」自动带出的尾程退货费结果（标准尺寸由计算器已有的大类收窄）
 *
 * <p>只用 record + 全局 SNAKE_CASE，不额外加 @JsonNaming：
 * 前端拿到的 key 就是 fee_usd / bill_weight_lb / weight_label 等，
 * 与本项目其它接口的命名习惯一致（LoginVO 是唯一例外，别照抄它）。</p>
 *
 * <p>matched 单独留一个布尔位：查不到档位时也要把这个事实告诉前端，
 * 让前端保留用户当前手填的值，而不是悄悄写成 0 —— 自动带出不能覆盖人工输入。</p>
 *
 * @param matched      是否命中字典行
 * @param tierCode     实际用于匹配的大类（small / large / oversize）
 * @param groupCode    命中的分组编码
 * @param groupName    命中的分组名称，前端提示里直接展示「取的是哪一档」
 * @param billWeightLb 本次计费重（lb）
 * @param billWeightOz 本次计费重换算成 oz
 * @param feeUsd       自动带出的尾程退货费 $/件；未命中时为 null
 * @param feeType      FLAT / FORMULA，前端据此提示「固定值」还是「按续重算出」
 * @param weightLabel  命中的重量区间原文，给用户看「为什么是这个价」
 * @param formulaText  公式档原文，展示用
 */
public record ReturnFeeLookupVO(
        boolean matched,
        String tierCode,
        String groupCode,
        String groupName,
        BigDecimal billWeightLb,
        BigDecimal billWeightOz,
        BigDecimal feeUsd,
        String feeType,
        String weightLabel,
        String formulaText
) {
    /** 未命中：费用留空，由前端保持用户已填的值 */
    public static ReturnFeeLookupVO missed(String tierCode, BigDecimal lb, BigDecimal oz) {
        return new ReturnFeeLookupVO(false, tierCode, null, null, lb, oz, null, null, null, null);
    }
}
