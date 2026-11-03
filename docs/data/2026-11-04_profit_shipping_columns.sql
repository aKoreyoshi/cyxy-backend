-- =====================================================================
-- 2026-11-04 利润测算方案补齐「配送方式」相关输入列
-- ---------------------------------------------------------------------
-- 背景：头程运费按配送方式分叉计算（与 product 表口径一致）：
--   AMAZON_SEND  亚马逊SEND：亚马逊后台整票核算配送费（$），
--                单件头程 = 配送总费用 × 汇率 ÷ 商品总数
--   SELF_CARRIER 自选承运人：单件头程 = 运费单价 × MAX(实重, 抛货体积重)
--                （NULL 视为 SELF_CARRIER，历史方案兼容）
--       原先 profit_plan 只存 first_leg_price（单价），SEND 模式三个
--       关键输入（总费用 / 件数 / 配送方式）无处落库，历史方案无法复算。
--
-- 本次只补 profit_plan 输入快照 4 列，结果表不加列：
--   profit_calc_result.first_leg_cost_cny 已存单件头程结果，足够复现；
--   输入以 plan 为准（与 logistics_provider / first_leg_price 的存法一致）。
--
-- 新增列：
--   shipping_method        VARCHAR(16)   配送方式 AMAZON_SEND / SELF_CARRIER
--   first_leg_method_code  VARCHAR(32)   头程方式 → dict_first_leg_method
--   shipping_total_fee_usd DECIMAL(12,2) 配送总费用 $（整票，仅 SEND 用）
--   total_units            INT UNSIGNED  商品总数（件，仅 SEND 用）
--
-- 脚本幂等：已存在同名列时跳过，可重复执行。
-- =====================================================================

USE cyxy;

-- 1) profit_plan.shipping_method
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'profit_plan' AND COLUMN_NAME = 'shipping_method'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `profit_plan` ADD COLUMN `shipping_method` VARCHAR(16) NULL DEFAULT NULL COMMENT ''配送方式：AMAZON_SEND 亚马逊SEND / SELF_CARRIER 自选承运人；NULL 视为 SELF_CARRIER（历史兼容）'' AFTER `first_leg_price`',
  'SELECT ''profit_plan.shipping_method 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) profit_plan.first_leg_method_code
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'profit_plan' AND COLUMN_NAME = 'first_leg_method_code'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `profit_plan` ADD COLUMN `first_leg_method_code` VARCHAR(32) NULL DEFAULT NULL COMMENT ''头程方式 → dict_first_leg_method.method_code'' AFTER `shipping_method`',
  'SELECT ''profit_plan.first_leg_method_code 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) profit_plan.shipping_total_fee_usd
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'profit_plan' AND COLUMN_NAME = 'shipping_total_fee_usd'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `profit_plan` ADD COLUMN `shipping_total_fee_usd` DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''配送总费用 $（整票货件，仅 AMAZON_SEND 模式使用）'' AFTER `first_leg_method_code`',
  'SELECT ''profit_plan.shipping_total_fee_usd 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) profit_plan.total_units
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'profit_plan' AND COLUMN_NAME = 'total_units'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `profit_plan` ADD COLUMN `total_units` INT UNSIGNED NULL DEFAULT NULL COMMENT ''商品总数（件，本票货件，仅 AMAZON_SEND 模式参与单件头程计算）'' AFTER `shipping_total_fee_usd`',
  'SELECT ''profit_plan.total_units 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
