package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author: Koreyoshi
 * @description: 全局计算参数，对应 sys_config 表
 * 承载燃油附加率、体积重除数、退货佣金扣留比例等常量。
 * 纯数学常数（如 cm 到 inch 的系数）不入此表，保留在代码常量中
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseDictEntity {

    /** 主键，配置键，如 FUEL_SURCHARGE_RATE */
    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;

    /** 配置值，统一字符串存，按 valueType 转换 */
    private String configValue;

    /** 值类型：NUMBER / STRING / BOOLEAN */
    private String valueType;

    /** 配置中文名 */
    private String configName;

    /** 说明 */
    private String remark;
}
