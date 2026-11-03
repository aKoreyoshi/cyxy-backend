package com.cyxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyxy.model.entity.ProductFbaFee;
import org.apache.ibatis.annotations.Insert;

/**
 * @author: Koreyoshi
 * @description: 产品 FBA 费用 Mapper，对应 product_fba_fee 表
 * 与 product 一一对应（uk_product_id 唯一索引），保存测算时整行 upsert
 * @version: 1.0
 * @date: 2026年11月1日, 23:40:00
 */
public interface ProductFbaFeeMapper extends BaseMapper<ProductFbaFee> {

    /**
     * 保存测算时整行写入：首次 INSERT，已有则 UPDATE。
     * <p>
     * 为什么不用 MP 的先查后写：实体带 @TableLogic，selectOne 会自动追加 is_deleted=0，
     * 软删过的行查不到 → 走 insert → 撞 uk_product_id 唯一索引报错。
     * 原生 ON DUPLICATE KEY UPDATE 同时把 is_deleted 复位为 0，天然绕开这个坑。
     * </p>
     */
    @Insert("""
            INSERT INTO product_fba_fee
              (product_id, inbound_config_fee_usd, monthly_storage_usd, fba_fee_usd,
               storage_fee_usd, commission_usd, avg_return_cost_cny, fba_total_cny,
               based_price_usd, calc_at, is_deleted)
            VALUES
              (#{productId}, #{inboundConfigFeeUsd}, #{monthlyStorageUsd}, #{fbaFeeUsd},
               #{storageFeeUsd}, #{commissionUsd}, #{avgReturnCostCny}, #{fbaTotalCny},
               #{basedPriceUsd}, #{calcAt}, 0)
            ON DUPLICATE KEY UPDATE
              inbound_config_fee_usd = VALUES(inbound_config_fee_usd),
              monthly_storage_usd    = VALUES(monthly_storage_usd),
              fba_fee_usd            = VALUES(fba_fee_usd),
              storage_fee_usd        = VALUES(storage_fee_usd),
              commission_usd         = VALUES(commission_usd),
              avg_return_cost_cny    = VALUES(avg_return_cost_cny),
              fba_total_cny          = VALUES(fba_total_cny),
              based_price_usd        = VALUES(based_price_usd),
              calc_at                = VALUES(calc_at),
              is_deleted             = 0
            """)
    void upsert(ProductFbaFee fee);
}
