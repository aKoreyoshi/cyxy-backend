-- =====================================================================
-- 2026-11-02 产品配送方式改造
-- ---------------------------------------------------------------------
-- 背景：在亚马逊后台创建货件时可选「亚马逊 SEND」，此时配送费由亚马逊
--       后台整票自动核算，不再产生「头程运费单价」参数；选「自己选择
--       承运人」则仍按 单价 × 计费重 计算。
-- product 新增 3 列：
--   shipping_method        配送方式（AMAZON_SEND / SELF_CARRIER）
--   shipping_total_fee_usd 配送总费用 $（整票，仅亚马逊 SEND 模式有值）
--   total_units            商品总数（件，本票货件总件数）
-- 脚本幂等：已存在同名列时跳过，可重复执行。
-- =====================================================================

USE cyxy;

-- 1) shipping_method（配送方式）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'shipping_method'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `shipping_method` VARCHAR(32) NULL DEFAULT NULL COMMENT ''配送方式：AMAZON_SEND=亚马逊SEND / SELF_CARRIER=自选承运人；NULL 视为 SELF_CARRIER（历史数据）'' AFTER `first_leg_method_code`',
  'SELECT ''shipping_method 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) shipping_total_fee_usd（配送总费用 $，整票）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'shipping_total_fee_usd'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `shipping_total_fee_usd` DECIMAL(12,4) NULL DEFAULT NULL COMMENT ''配送总费用 $（整票货件，亚马逊 SEND 模式由亚马逊后台核算；自选承运人模式为 NULL）'' AFTER `first_leg_unit_price`',
  'SELECT ''shipping_total_fee_usd 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) total_units（商品总数，件）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'total_units'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `total_units` INT UNSIGNED NULL DEFAULT NULL COMMENT ''商品总数（件），本票货件总件数'' AFTER `shipping_total_fee_usd`',
  'SELECT ''total_units 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product'
  AND COLUMN_NAME IN ('shipping_method', 'shipping_total_fee_usd', 'total_units')
ORDER BY ORDINAL_POSITION;
