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
 * @description: 类目退货阈值字典，对应 dict_category_return_threshold 表
 * 与佣金类目是两套互不相干的官方字典，命名也不一致，因此不合并、不联动，
 * 由用户在每轮测算时单独手选
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_category_return_threshold")
public class DictCategoryReturnThreshold extends BaseDictEntity {

    /** 主键，业务编码 */
    @TableId(value = "threshold_code", type = IdType.INPUT)
    private String thresholdCode;

    /** 类目名称 */
    private String categoryName;

    /** 退货阈值，0.113000 表示 11.3% */
    private BigDecimal thresholdRate;

    /** 说明 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 生效起始日 */
    private LocalDate validFrom;

    /** 生效截止日 */
    private LocalDate validTo;
}
