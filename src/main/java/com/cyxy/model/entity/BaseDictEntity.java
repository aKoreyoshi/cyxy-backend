package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author: Koreyoshi
 * @description: 字典实体公共基类，适用于「业务编码做主键」的表
 * 适用范围：dict_category_commission / dict_category_return_threshold /
 * dict_fba_size_tier / dict_first_leg_method / sys_config
 * 这五张表没有自增 id，主键为字符串业务编码，需由子类用 @TableId(type = IdType.INPUT) 指定
 * @version: 1.0
 * @date: 2026年10月31日, 01:15:00
 */
@Data
@EqualsAndHashCode
public abstract class BaseDictEntity {

    /** 创建时间，数据库默认 CURRENT_TIMESTAMP(3) */
    private LocalDateTime createdTime;

    /** 更新时间，数据库 ON UPDATE CURRENT_TIMESTAMP(3) */
    private LocalDateTime updatedTime;

    /** 软删标记：0 未删除 / 1 已删除 */
    @TableLogic
    private Integer isDeleted;
}
