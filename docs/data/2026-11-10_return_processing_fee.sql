-- ============================================================================
-- 尾程退货处理费字典表（Returns processing fee）
-- 数据来源：D:/Workbuddy/尾程退货费.xlsx（Sheet1，R4~R29 共 26 行）
-- 原表口径：除服装和鞋靴之外的所有商品；对超过阈值的退货商品收取；
--           原表标注「适用于自 2027 年 2 月 1 日起发货的商品」。
--
-- ⚠️ 分组以用户 2026-11-10 提供的口径为准（原表没有分组标题行）：
--    R4~R11   小号标准尺寸          SMALL_STANDARD      8 档，2oz 步长
--    R12~R24  大号标准尺寸          LARGE_STANDARD      12 固定档 + 3~20lb 续重档
--    R25      大号大件              LARGE_BULKY         0~50lb，起步价 $6.74
--    R26      超大尺寸：0-50磅      OVERSIZE_0_50       0~50lb，起步价 $26.33
--    R27      超大件：50-70磅       OVERSIZE_50_70      50~70lb（不含 50）
--    R28      超大件：70-150磅      OVERSIZE_70_150     70~150lb（不含 70）
--    R29      超大件：150磅以上     OVERSIZE_150_PLUS   >150lb
--
-- ⚠️ 三条建模决策（为什么这么设计）：
-- 1) 原表 A 列对 R27 / R28 写的是「0 至 70 磅」「0 至 150 磅」，
--    但用户给的分组名明确了 50-70 / 70-150 这一段边界，且与 R26 的 0-50 首尾相接。
--    所以 weight_min_oz / weight_max_oz（**用于匹配**）按分组名修正为 800~1120oz、
--    1120~2400oz；A 列原文原样保留在 weight_label 里，只作展示，不参与判定。
-- 2) 取值只依据「计费重」。标准尺寸段由计算器自己算出的尺寸分段收窄
--    （small / large，不需要用户额外填写）；大件段不再按尺寸细分，
--    纯粹按计费重落在哪个区间取。R25 与 R26 都是 0~50lb，按表中顺序取第一条
--    （即「大号大件」），实际业务以亚马逊后台尺寸档为准，值仍允许手改。
-- 3) 重量统一换算成 oz（1 lb = 16 oz），区间左开右闭，与 dict_fba_rate 一致；
--    weight_max_oz 为 NULL 表示不限。后 6 行是「起步价 + 续重单价」的公式档，
--    拆成 base_fee_usd / step_fee_usd / step_unit / step_from_oz 四个可计算字段，
--    同时保留 formula_text 原文供参数列表页逐字呈现。
--
-- ⚠️ valid_from 特意留 NULL：原表标注 2027-02-01 才生效，若按日期过滤，
--    当前（2026-11）查不到任何数据、自动带出会直接失效。生效日写进 remark 留痕。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + 动态加列（兼容已建过的库）+ INSERT ... ON DUPLICATE
--       KEY UPDATE（唯一键 tier_code+sort_no），重复执行结果一致。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `dict_return_processing_fee` (
  `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `tier_code`       VARCHAR(32)      NOT NULL              COMMENT '大类：small 小号标准 / large 大号标准 / oversize 大件（含全部 4 个大件分组）',
  `sort_no`         INT              NOT NULL DEFAULT 0    COMMENT '同大类内的匹配顺序；重叠档取序号最小的一行',
  `weight_min_oz`   DECIMAL(10,4)    NOT NULL DEFAULT 0    COMMENT '重量下界 oz（左开，用于匹配）',
  `weight_max_oz`   DECIMAL(10,4)    NULL     DEFAULT NULL COMMENT '重量上界 oz（右闭，用于匹配），NULL 表示不限',
  `weight_label`    VARCHAR(64)      NOT NULL              COMMENT '原表 A 列区间文字，仅展示用，不参与判定',
  `fee_type`        VARCHAR(16)      NOT NULL DEFAULT 'FLAT' COMMENT '计费形态：FLAT 固定值 / FORMULA 起步价+续重',
  `fee_usd`         DECIMAL(10,2)    NULL     DEFAULT NULL COMMENT '固定尾程退货费 $/件（仅 FLAT）',
  `base_fee_usd`    DECIMAL(10,2)    NULL     DEFAULT NULL COMMENT '起步价 $（仅 FORMULA）',
  `step_fee_usd`    DECIMAL(10,4)    NULL     DEFAULT NULL COMMENT '每个步进单位的加价 $（仅 FORMULA）',
  `step_unit`       VARCHAR(16)      NULL     DEFAULT NULL COMMENT '步进单位：OZ4 每 4 盎司 / LB 每磅',
  `step_from_oz`    DECIMAL(10,4)    NULL     DEFAULT NULL COMMENT '续重起算点 oz：只有超出这部分的重量才累加',
  `formula_text`    VARCHAR(256)     NULL     DEFAULT NULL COMMENT '原表公式文字，展示用',
  `remark`          VARCHAR(512)     NULL     DEFAULT NULL COMMENT '备注',
  `valid_from`      DATE             NULL     DEFAULT NULL COMMENT '生效起始日（当前留空，见文件头说明）',
  `valid_to`        DATE             NULL     DEFAULT NULL COMMENT '生效截止日',
  `created_time`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`      INT              NOT NULL DEFAULT 0    COMMENT '软删标记，0-未删除，1-删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tier_sort` (`tier_code`, `sort_no`),
  KEY `idx_lookup` (`tier_code`, `weight_min_oz`, `weight_max_oz`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='尾程退货处理费字典（按计费重查值，7 个分组）';

-- 已建过该表的库需要补两列。MySQL 8.0.17 不支持 ADD COLUMN IF NOT EXISTS，
-- 所以先查 information_schema 再动态执行；两列一起加，判断 group_code 即可。
SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `dict_return_processing_fee` ADD COLUMN `group_code` VARCHAR(32) NULL COMMENT ''分组编码'' AFTER `tier_code`, ADD COLUMN `group_name` VARCHAR(64) NULL COMMENT ''分组名称（原表无标题行，由业务口径给定）'' AFTER `group_code`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dict_return_processing_fee'
    AND COLUMN_NAME = 'group_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 26 行数据，严格按原表 R4~R29 顺序；sort_no 在各 tier_code 内从 1 重新计数
INSERT INTO `dict_return_processing_fee`
  (`tier_code`, `sort_no`, `group_code`, `group_name`,
   `weight_min_oz`, `weight_max_oz`, `weight_label`,
   `fee_type`, `fee_usd`, `base_fee_usd`, `step_fee_usd`, `step_unit`, `step_from_oz`,
   `formula_text`, `remark`)
VALUES
  -- 【小号标准尺寸】R4~R11：8 档，2oz 步长
  ('small', 1,  'SMALL_STANDARD', '小号标准尺寸',    0.0000,   2.0000, '不超过 2 盎司',                 'FLAT', 1.78, NULL, NULL, NULL, NULL,  NULL, '除服装和鞋靴之外的所有商品；对超过阈值的退货商品收取；原表标注适用于自 2027-02-01 起发货的商品'),
  ('small', 2,  'SMALL_STANDARD', '小号标准尺寸',    2.0000,   4.0000, '2 至 4 盎司（不含 2 盎司）',     'FLAT', 1.84, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('small', 3,  'SMALL_STANDARD', '小号标准尺寸',    4.0000,   6.0000, '4 至 6 盎司（不含 4 盎司）',     'FLAT', 1.90, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('small', 4,  'SMALL_STANDARD', '小号标准尺寸',    6.0000,   8.0000, '6 至 8 盎司（不含 6 盎司）',     'FLAT', 1.96, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('small', 5,  'SMALL_STANDARD', '小号标准尺寸',    8.0000,  10.0000, '8 至 10 盎司（不含 8 盎司）',    'FLAT', 2.02, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('small', 6,  'SMALL_STANDARD', '小号标准尺寸',   10.0000,  12.0000, '10 至 12 盎司（不含 10 盎司）',  'FLAT', 2.08, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('small', 7,  'SMALL_STANDARD', '小号标准尺寸',   12.0000,  14.0000, '12 至 14 盎司（不含 12 盎司）',  'FLAT', 2.14, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('small', 8,  'SMALL_STANDARD', '小号标准尺寸',   14.0000,  16.0000, '14 至 16 盎司（不含 14 盎司）',  'FLAT', 2.21, NULL, NULL, NULL, NULL,  NULL, NULL),

  -- 【大号标准尺寸】R12~R23：12 个固定档
  ('large', 1,  'LARGE_STANDARD', '大号标准尺寸',    0.0000,   4.0000, '不超过 4 盎司',                 'FLAT', 2.36, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 2,  'LARGE_STANDARD', '大号标准尺寸',    4.0000,   8.0000, '4 至 8 盎司（不含 4 盎司）',     'FLAT', 2.70, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 3,  'LARGE_STANDARD', '大号标准尺寸',    8.0000,  12.0000, '8 至 12 盎司（不含 8 盎司）',    'FLAT', 3.05, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 4,  'LARGE_STANDARD', '大号标准尺寸',   12.0000,  16.0000, '12 至 16 盎司（不含 12 盎司）',  'FLAT', 3.39, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 5,  'LARGE_STANDARD', '大号标准尺寸',   16.0000,  20.0000, '1 至 1.25 磅（不含 1 磅）',      'FLAT', 3.70, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 6,  'LARGE_STANDARD', '大号标准尺寸',   20.0000,  24.0000, '1.25 至 1.5 磅（不含 1.25 磅）', 'FLAT', 4.01, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 7,  'LARGE_STANDARD', '大号标准尺寸',   24.0000,  28.0000, '1.5 至 1.75 磅（不含 1.5 磅）',  'FLAT', 4.32, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 8,  'LARGE_STANDARD', '大号标准尺寸',   28.0000,  32.0000, '1.75 至 2 磅（不含 1.75 磅）',   'FLAT', 4.63, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 9,  'LARGE_STANDARD', '大号标准尺寸',   32.0000,  36.0000, '2 至 2.25 磅（不含 2 磅）',      'FLAT', 4.66, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 10, 'LARGE_STANDARD', '大号标准尺寸',   36.0000,  40.0000, '2.25 至 2.5 磅（不含 2.25 磅）', 'FLAT', 4.68, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 11, 'LARGE_STANDARD', '大号标准尺寸',   40.0000,  44.0000, '2.5 至 2.75 磅（不含 2.5 磅）',  'FLAT', 4.71, NULL, NULL, NULL, NULL,  NULL, NULL),
  ('large', 12, 'LARGE_STANDARD', '大号标准尺寸',   44.0000,  48.0000, '2.75 至 3 磅（不含 2.75 磅）',   'FLAT', 4.73, NULL, NULL, NULL, NULL,  NULL, NULL),
  -- R24：3~20lb 续重档，仍属「大号标准尺寸」
  ('large', 13, 'LARGE_STANDARD', '大号标准尺寸',   48.0000, 320.0000, '3 至 20 磅（不含 3 磅）',        'FORMULA', NULL, 5.00, 0.0500, 'OZ4',  48.0000, '$5.00 + $0.05/4 盎司（超出首重 3 磅的部分）', '超出 3lb 的部分按每 4oz 步进累加'),

  -- 【大号大件】R25：0~50lb，起步价 $6.74
  ('oversize', 1, 'LARGE_BULKY',       '大号大件',              0.0000,  800.0000, '0 至 50 磅',              'FORMULA', NULL,  6.74, 0.3200, 'LB',   16.0000, '$6.74 + $0.32/磅（超出首磅的部分）',            '与「超大尺寸：0-50磅」重量区间重叠，按表中顺序优先取这一行'),
  -- 【超大尺寸：0-50磅】R26
  ('oversize', 2, 'OVERSIZE_0_50',     '超大尺寸：0-50磅',       0.0000,  800.0000, '0 至 50 磅',              'FORMULA', NULL, 26.33, 0.3800, 'LB',   16.0000, '$26.33 + 超出首磅的部分每磅 $0.38',              NULL),
  -- 【超大件：50-70磅】R27：匹配边界按分组名修正为 800~1120oz，A 列原文保留展示
  ('oversize', 3, 'OVERSIZE_50_70',    '超大件：50-70磅(不含50磅)', 800.0000, 1120.0000, '0 至 70 磅',          'FORMULA', NULL, 40.12, 0.7500, 'LB',  816.0000, '$40.12 + 超出首重 51 磅的部分每磅 $0.75',        'A 列原文写「0 至 70 磅」，匹配区间按分组名 50-70 磅'),
  -- 【超大件：70-150磅】R28：匹配边界按分组名修正为 1120~2400oz
  ('oversize', 4, 'OVERSIZE_70_150',   '超大件：70-150磅(不含70磅)', 1120.0000, 2400.0000, '0 至 150 磅',        'FORMULA', NULL, 52.45, 0.7500, 'LB', 1136.0000, '$52.45 + $0.75/磅（超出 71 磅的部分）',          'A 列原文写「0 至 150 磅」，匹配区间按分组名 70-150 磅'),
  -- 【超大件：150磅以上】R29
  ('oversize', 5, 'OVERSIZE_150_PLUS', '超大件：150磅以上(不含150磅)', 2400.0000, NULL, '150 磅以上（不含 150 磅）', 'FORMULA', NULL, 157.35, 0.1900, 'LB', 2416.0000, '$157.35 + $0.19/磅（超出 151 磅的部分）',        NULL)
ON DUPLICATE KEY UPDATE
  `group_code`     = VALUES(`group_code`),
  `group_name`     = VALUES(`group_name`),
  `weight_min_oz`  = VALUES(`weight_min_oz`),
  `weight_max_oz`  = VALUES(`weight_max_oz`),
  `weight_label`   = VALUES(`weight_label`),
  `fee_type`       = VALUES(`fee_type`),
  `fee_usd`        = VALUES(`fee_usd`),
  `base_fee_usd`   = VALUES(`base_fee_usd`),
  `step_fee_usd`   = VALUES(`step_fee_usd`),
  `step_unit`      = VALUES(`step_unit`),
  `step_from_oz`   = VALUES(`step_from_oz`),
  `formula_text`   = VALUES(`formula_text`),
  `remark`         = VALUES(`remark`),
  `is_deleted`     = 0;

-- 验证：7 个分组、26 行
SELECT `group_code`, `group_name`, COUNT(*) AS rows_count,
       SUM(`fee_type` = 'FLAT') AS flat_rows,
       SUM(`fee_type` = 'FORMULA') AS formula_rows
FROM `dict_return_processing_fee`
WHERE `is_deleted` = 0
GROUP BY `group_code`, `group_name`
ORDER BY MIN(`id`);
