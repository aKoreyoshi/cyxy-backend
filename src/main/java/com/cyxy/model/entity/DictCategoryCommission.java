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
 * @description: 佣金类目字典，对应 dict_category_commission 表
 * 单一真源：产品录入的类目下拉与计算器的佣金类目均取自本表（共 46 项）
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_category_commission")
public class DictCategoryCommission extends BaseDictEntity {

    /** 主键，业务编码 */
    @TableId(value = "category_code", type = IdType.INPUT)
    private String categoryCode;

    /** 类目中文名 */
    private String categoryName;

    /** 费率模式：FLAT 单一费率 / FULL 跳档整套按新费率 / MARGINAL 累进分段 */
    private String feeMode;

    /** 单一费率，feeMode=FLAT 时有效，0.1500 表示 15% */
    private BigDecimal flatRate;

    /** 最低佣金 $，NULL 表示无下限 */
    private BigDecimal minCommissionUsd;

    /** 是否需要亚马逊类目审核：0 否 / 1 是 */
    private Integer needApproval;

    /** 费率说明原文 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 生效起始日 */
    private LocalDate validFrom;

    /** 生效截止日，NULL 表示长期有效 */
    private LocalDate validTo;
}
