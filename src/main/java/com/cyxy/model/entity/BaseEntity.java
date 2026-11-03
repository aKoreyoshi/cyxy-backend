package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description: 实体公共基类，适用于「自增主键」的表
 * 适用范围：product / profit_plan / profit_calc_result /
 * dict_category_commission_tier / dict_exchange_rate / dict_fba_rate / dict_fba_step_rate
 * 其余以业务编码为主键的字典表请继承 BaseDictEntity
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode
public abstract class BaseEntity {

    /** 物理主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建时间，数据库默认 CURRENT_TIMESTAMP(3) */
    private LocalDateTime createdTime;

    /** 更新时间，数据库 ON UPDATE CURRENT_TIMESTAMP(3) */
    private LocalDateTime updatedTime;

    /** 软删标记：0 未删除 / 1 已删除 */
    @TableLogic
    private Integer isDeleted;
}
