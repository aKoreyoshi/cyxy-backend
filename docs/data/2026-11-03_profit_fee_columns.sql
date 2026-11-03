-- =====================================================================
-- 2026-11-03 利润测算方案 / 结果快照补齐 FBA 费用输入列
-- ---------------------------------------------------------------------
-- 背景：入库配置费、月度仓储费改为在计算器「FBA费用计算」卡片里手填，
--       并参与利润计算（前期没有这两笔费用时填 0，结果不受影响）。
--       原先只有 product_fba_fee 存了这两列，profit_plan（输入快照）和
--       profit_calc_result（结果快照）都缺，导致历史方案无法复算。
--
-- 本次只补字段定义，不改任何计算逻辑、不加接口、不动前端。
--
-- 新增列（两张表各 2 列，类型与 product_fba_fee 保持一致）：
--   inbound_config_fee_usd  DECIMAL(12,6)  入库配置费 $/件
--   monthly_storage_usd     DECIMAL(12,6)  月度仓储费 $/件
--
-- 设计约定：
--   1. 允许 NULL：旧方案本来就没有这两笔费用，应用层按 null -> 0 归一
--   2. 位置放在 storage_fee_usd 之后，与同类「$/件」费用相邻
--
-- 脚本幂等：已存在同名列时跳过，可重复执行。
-- =====================================================================

USE cyxy;

-- 1) profit_plan.inbound_config_fee_usd
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'profit_plan' AND COLUMN_NAME = 'inbound_config_fee_usd'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `profit_plan` ADD COLUMN `inbound_config_fee_usd` DECIMAL(12,6) NULL DEFAULT NULL COMMENT ''入库配置费 $/件，计算器「FBA费用计算」卡片手填；参与利润计算，空按 0 处理'' AFTER `storage_fee_usd`',
  'SELECT ''profit_plan.inbound_config_fee_usd 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) profit_plan.monthly_storage_usd
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'profit_plan' AND COLUMN_NAME = 'monthly_storage_usd'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `profit_plan` ADD COLUMN `monthly_storage_usd` DECIMAL(12,6) NULL DEFAULT NULL COMMENT ''月度仓储费 $/件，计算器「FBA费用计算」卡片手填；参与利润计算，空按 0 处理'' AFTER `inbound_config_fee_usd`',
  'SELECT ''profit_plan.monthly_storage_usd 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) profit_calc_result.inbound_config_fee_usd
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'profit_calc_result' AND COLUMN_NAME = 'inbound_config_fee_usd'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `profit_calc_result` ADD COLUMN `inbound_config_fee_usd` DECIMAL(12,6) NULL DEFAULT NULL COMMENT ''入库配置费 $/件（输入冗余），空按 0 处理'' AFTER `storage_fee_usd`',
  'SELECT ''profit_calc_result.inbound_config_fee_usd 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) profit_calc_result.monthly_storage_usd
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'profit_calc_result' AND COLUMN_NAME = 'monthly_storage_usd'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `profit_calc_result` ADD COLUMN `monthly_storage_usd` DECIMAL(12,6) NULL DEFAULT NULL COMMENT ''月度仓储费 $/件（输入冗余），空按 0 处理'' AFTER `inbound_config_fee_usd`',
  'SELECT ''profit_calc_result.monthly_storage_usd 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
