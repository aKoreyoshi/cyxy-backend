package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * @author: Koreyoshi
 * @description: 阶梯佣金字典，对应 dict_category_commission_tier 表
 * 承载 feeMode=FULL（跳档）与 MARGINAL（累进）两种模式的档位明细；
 * FLAT 模式不读本表。此表在原 Excel 中未实现，属于新系统新增
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_category_commission_tier")
public class DictCategoryCommissionTier extends BaseEntity {

    /** 外键，指向 dict_category_commission.category_code */
    private String categoryCode;

    /** 档位序号 */
    private Integer tierNo;

    /** 该档价格下界（含） */
    private BigDecimal priceMinUsd;

    /** 该档价格上界（含），NULL 表示无上限 */
    private BigDecimal priceMaxUsd;

    /** 该档费率 */
    private BigDecimal tierRate;

    /** 该档最低佣金 $ */
    private BigDecimal minCommissionUsd;

    /** 档位说明 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;
}
