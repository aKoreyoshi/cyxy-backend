package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author: Koreyoshi
 * @description: FBA 尺寸分段规则，对应 dict_fba_size_tier 表
 * weightBasis 字段是核心：分段判定永远用实重，但查费率时小号标准用 ACTUAL、
 * 大号标准与大件用 BILLABLE。此口径写进表里，避免在 Java 中硬编码
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_fba_size_tier")
public class DictFbaSizeTier extends BaseDictEntity {

    /** 主键：small / large / oversize */
    @TableId(value = "tier_code", type = IdType.INPUT)
    private String tierCode;

    /** 分段中文名 */
    private String tierName;

    /** 判定顺序：小号 → 大号 → 兜底大件 */
    private Integer sortNo;

    /** 最长边上限 inch，NULL 表示不限 */
    private BigDecimal maxLengthInch;

    /** 次长边上限 inch */
    private BigDecimal maxWidthInch;

    /** 最短边上限 inch */
    private BigDecimal maxHeightInch;

    /** 重量上限 lb */
    private BigDecimal maxWeightLb;

    /** 查费率时取值：ACTUAL 按实重 / BILLABLE 按计费重 */
    private String weightBasis;

    /** 备注 */
    private String remark;

    /** 生效起始日 */
    private LocalDate validFrom;

    /** 生效截止日 */
    private LocalDate validTo;
}
