package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * @author: Koreyoshi
 * @description: 头程方式与默认单价，对应 dict_first_leg_method 表
 * 关键在 volumeDivisor：材积比 5000/6000 由运输方式决定（海运偏 5000、空运偏 6000），
 * 属行业规则，挂在本表后可随方式自动带出，不必让用户手动选择
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_first_leg_method")
public class DictFirstLegMethod extends BaseDictEntity {

    /** 主键：SEA / SEA_EXPRESS / AIR / EXPRESS */
    @TableId(value = "method_code", type = IdType.INPUT)
    private String methodCode;

    /** 方式名称：海运 / 海运快线 / 空运 / 快递 */
    private String methodName;

    /** 默认头程单价 ￥/kg */
    private BigDecimal defaultUnitPrice;

    /** 材积比，长×宽×高(cm) ÷ 此值 = 抛货重量 kg */
    private Integer volumeDivisor;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
