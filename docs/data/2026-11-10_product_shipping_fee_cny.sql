-- =====================================================================
-- 2026-11-10 product 表配送费用口径调整
-- ---------------------------------------------------------------------
-- 背景：产品档案改为「一行 = 一种产品」，不再按整票货件记账。
--       原 shipping_total_fee_usd 记的是亚马逊后台给整票货件核算的配送费，
--       total_units 记的是整票总件数；这与「每行只描述当前产品」的新口径冲突。
--
-- 本次三处改动（只动 product 表，profit_plan 表结构保持不变）：
--   1) shipping_total_fee_usd 语义：配送总费用 $ —— 仍仅 AMAZON_SEND 模式填写，
--      但改为「当前产品」的费用，不再是整票货件费用（列名不变、类型不变）。
--   2) total_units 语义：商品总数量（当前产品的件数），不再记单票货件总数。
--   3) 在 total_units 之后新增 shipping_total_fee_cny：配送总费用 ￥，
--      仅 SELF_CARRIER（自己选择承运人）模式填写，与「头程单价」同组使用。
--
-- ⚠️ 两个「配送总费用」按配送方式严格二选一，绝不混用、也不互相换算：
--      AMAZON_SEND    → shipping_total_fee_usd（$）  + total_units
--      SELF_CARRIER   → shipping_total_fee_cny（￥） + first_leg_unit_price + total_units
--    列名后缀 usd / cny 就是第一层区分；COMMENT、表单标签、条件显示是后几层区分。
--    之所以不复用同一个字段：两种模式下金额币种不同、来源不同（亚马逊后台核算
--    vs 自己找物流商结算），合并后历史数据无法判断当初填的是哪一种。
--
-- 头程计算公式不变：SEND 仍是「费用 × 汇率 ÷ 件数」，只是分子分母都从
-- 「整票」换成「当前产品」，两者同步缩小，单件结果口径保持一致。
-- 新增的 ￥ 列本期只做录入与留存，不参与利润计算（本次不改动任何算法）。
--
-- 脚本幂等：MODIFY COLUMN 重复执行结果一致；ADD COLUMN 先查 information_schema；
--           末尾再把新列钉回 total_units 之后，防止重复执行打乱列顺序。
-- 执行时请用 utf8mb4 客户端字符集，否则中文 COMMENT 会变乱码：
--   mysql --default-character-set=utf8mb4 -uroot -p cyxy < 本文件
-- =====================================================================

USE cyxy;

-- ⚠️ MODIFY COLUMN 必须显式写 AFTER：MySQL 8 在重建表时可能改变列的相对顺序，
--    不钉住位置的话，新增的 ￥ 列就不再紧跟 total_units（用户要求它就在下一列）。
-- 1) shipping_total_fee_usd：改注释（口径）+ 钉回原位置（物流商之后）
ALTER TABLE `product`
  MODIFY COLUMN `shipping_total_fee_usd` DECIMAL(12,4) NULL DEFAULT NULL
  COMMENT '配送总费用 $（当前产品，仅 AMAZON_SEND 亚马逊SEND 模式填写；每行记录单种产品，不再代表整票货件。与 shipping_total_fee_cny 按配送方式二选一）'
  AFTER `logistics_provider`;

-- 2) total_units：改注释（口径）+ 钉回原位置（头程单价之后）
ALTER TABLE `product`
  MODIFY COLUMN `total_units` INT UNSIGNED NULL DEFAULT NULL
  COMMENT '商品总数量（件，当前产品；每行记录单种产品，不记单票货件总数；两种配送方式都填）'
  AFTER `first_leg_unit_price`;

-- 3) shipping_total_fee_cny：紧跟 total_units 之后新增
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'shipping_total_fee_cny'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `shipping_total_fee_cny` DECIMAL(12,4) NULL DEFAULT NULL COMMENT ''配送总费用 ￥（当前产品，仅 SELF_CARRIER 自己选择承运人模式填写，与头程单价同组；AMAZON_SEND 模式为 NULL。与 shipping_total_fee_usd 是两种配送方式各自专用的字段，不可混用）'' AFTER `total_units`',
  'SELECT ''product.shipping_total_fee_cny 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 最后把 ￥ 列钉在 total_units 之后。
--    ADD 时已经写了 AFTER，但重复执行本脚本时第 1、2 步的 MODIFY 会移动列，
--    可能把这组费用的相对顺序打乱；这里再钉一次，保证跑几次结果都一样。
ALTER TABLE `product`
  MODIFY COLUMN `shipping_total_fee_cny` DECIMAL(12,4) NULL DEFAULT NULL
  COMMENT '配送总费用 ￥（当前产品，仅 SELF_CARRIER 自己选择承运人模式填写，与头程单价同组；AMAZON_SEND 模式为 NULL。与 shipping_total_fee_usd 是两种配送方式各自专用的字段，不可混用）'
  AFTER `total_units`;

-- 验证：看列顺序与口径是否都落到位
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product'
  AND COLUMN_NAME IN ('first_leg_unit_price', 'shipping_total_fee_usd',
                      'total_units', 'shipping_total_fee_cny')
ORDER BY ORDINAL_POSITION;
