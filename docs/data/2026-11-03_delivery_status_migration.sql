-- =====================================================================
-- 2026-11-03 产品配送状态改造（预计送达 / 确认送达 / 撤销确认）
-- ---------------------------------------------------------------------
-- 背景：录入或编辑产品时可填写「预计送达时间」，填了但还没确认收货时
--       前台显示「配送中」；货真正到了以后在前台点「确认送达」，系统
--       取当时的时间（统一北京时间）与预计送达时间对比，按自然日判定
--       提前 / 准时 / 晚送达；确认后这几个字段锁定不可再改，如需修正
--       走「撤销确认」回到配送中。
--
-- product 新增 4 列：
--   expected_delivery_date  预计送达日期（自然日，精确到天）
--   actual_delivery_time    实际送达时间（确认那一刻，服务端生成）
--   delivery_status         配送状态（NOT_SET/IN_TRANSIT/EARLY/ON_TIME/LATE）
--   delay_days              差值天数，负=提前 / 0=准时 / 正=晚几天
--
-- 设计约定：
--   1. 「是否已确认」用 actual_delivery_time 是否为空表达，不额外加 is_locked
--   2. 状态是冗余字段，为的是列表页能直接筛选/排序，判定逻辑仍以两个时间为准
--   3. 时间统一 Asia/Shanghai，由应用层显式指定时区，不依赖 JVM 默认时区
--
-- 脚本幂等：已存在同名列时跳过，可重复执行。
-- =====================================================================

USE cyxy;

-- 1) expected_delivery_date（预计送达日期）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'expected_delivery_date'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `expected_delivery_date` DATE NULL DEFAULT NULL COMMENT ''预计送达日期（自然日），录入或编辑时填写；确认送达后锁定不可改'' AFTER `total_units`',
  'SELECT ''expected_delivery_date 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) actual_delivery_time（实际送达时间）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'actual_delivery_time'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `actual_delivery_time` DATETIME(3) NULL DEFAULT NULL COMMENT ''实际送达时间，点确认送达时由服务端按北京时间生成；非空即视为已确认，锁定配送相关字段'' AFTER `expected_delivery_date`',
  'SELECT ''actual_delivery_time 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) delivery_status（配送状态）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'delivery_status'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `delivery_status` VARCHAR(16) NULL DEFAULT NULL COMMENT ''配送状态：NOT_SET=未填写 / IN_TRANSIT=配送中 / EARLY=提前送达 / ON_TIME=准时送达 / LATE=晚送达'' AFTER `actual_delivery_time`',
  'SELECT ''delivery_status 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) delay_days（送达天数差）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'delay_days'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `delay_days` INT NULL DEFAULT NULL COMMENT ''送达天数差 = 实际送达日期 - 预计送达日期（自然日）；负数提前、0 准时、正数晚几天'' AFTER `delivery_status`',
  'SELECT ''delay_days 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 辅助索引：列表页按配送状态筛选/排序
SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product' AND INDEX_NAME = 'idx_delivery_status'
);
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE `product` ADD KEY `idx_delivery_status` (`delivery_status`)',
  'SELECT ''idx_delivery_status 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'cyxy' AND TABLE_NAME = 'product'
  AND COLUMN_NAME IN ('expected_delivery_date', 'actual_delivery_time', 'delivery_status', 'delay_days')
ORDER BY ORDINAL_POSITION;
