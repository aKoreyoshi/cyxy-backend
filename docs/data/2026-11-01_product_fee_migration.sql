-- =============================================================
-- 产品费用相关结构调整（product 补字段 + 新增 product_fba_fee）
-- 日期：2026-11-01
-- 目标库：cyxy（MySQL 8.0）
--
-- 幂等设计：
--   1) 加列：先查 information_schema.COLUMNS，已存在则跳过（MySQL 不支持 ADD COLUMN IF NOT EXISTS）
--   2) 常量：INSERT ... ON DUPLICATE KEY UPDATE
-- 重复执行安全，不会报错也不会产生重复数据。
-- =============================================================

SET NAMES utf8mb4;


-- ---------------------------------------------------------------------
-- 1. product 补充字段
-- ---------------------------------------------------------------------

-- 1.1 尺寸分段：由「包装长/宽/高 + 含包装实重」自动匹配，落 dict_fba_size_tier.tier_code
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'size_tier_code');
SET @sql := IF(@exist = 0,
    'ALTER TABLE `product` ADD COLUMN `size_tier_code` VARCHAR(32) NULL DEFAULT NULL COMMENT ''尺寸分段 small/large/oversize，由长宽高与实重自动匹配'' AFTER `height_cm`',
    'SELECT ''skip: product.size_tier_code already exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 关税费 ￥/件：当前手工填写；后续可改为 (采购单价 + 包材) × 关税率 自动计算
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'tariff_fee');
SET @sql := IF(@exist = 0,
    'ALTER TABLE `product` ADD COLUMN `tariff_fee` DECIMAL(12,4) NULL DEFAULT NULL COMMENT ''关税费￥/件，当前手工填写，后续可改自动计算'' AFTER `packaged_weight_g`',
    'SELECT ''skip: product.tariff_fee already exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 2. sys_config 常量
-- ---------------------------------------------------------------------

-- 2.1 原 DEFAULT_STORAGE_FEE_USD 实为「入库配置服务费」，改名澄清，避免与月度仓储费混淆
UPDATE sys_config
SET config_key   = 'INBOUND_CONFIG_FEE_USD',
    config_name  = '入库配置服务费',
    remark       = '入库配置服务费 $/件（Amazon 官方口径，标准尺寸约 0.27~0.32）；非月度仓储费'
WHERE config_key = 'DEFAULT_STORAGE_FEE_USD';

-- 2.2 新增常量
INSERT INTO sys_config
    (config_key, config_value, value_type, config_name, remark)
VALUES
    ('DEFAULT_TARIFF_RATE',        '0.25', 'NUMBER', '默认关税税率',
     '占采购价；Excel 参数设置 B14。占位值，需按产品 HS 编码与当年实际对华税率修正。当前为全局统一值；若日后要按产品覆盖，再给 product 加可空的 tariff_rate（NULL 即用此处）'),
    ('MONTHLY_STORAGE_RATE_USD',   '0.78', 'NUMBER', '月度仓储费率',
     '$/立方英尺；标准尺寸 1-9 月约 0.78，10-12 月约 2.40；Excel 参数设置 B10'),
    ('STORAGE_TURNOVER_MONTHS',    '1.5',  'NUMBER', '预计库存周转月数',
     '从入仓到售完平均压几个月，用于估算仓储费；Excel 参数设置 B11')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    value_type   = VALUES(value_type),
    config_name  = VALUES(config_name),
    remark       = VALUES(remark);


-- ---------------------------------------------------------------------
-- 3. 新增 product_fba_fee（产品 FBA 费用，1:1）
--
-- 填充节奏：
--   阶段一（录入产品）：只写 inbound_config_fee_usd / monthly_storage_usd
--           这两项只依赖「体积 + 全局参数」，录入即确定，其余留空
--   阶段二（利润测算）：回填 fba_fee_usd / storage_fee_usd(合仓费) /
--           commission_usd / avg_return_cost_cny / fba_total_cny
--
-- 与 profit_calc_result 的分工（不是重复）：
--   product_fba_fee      产品级「当前费用速览」，1:1，可被反复部分更新
--   profit_calc_result   方案级「历史快照」，每条不可变，用于回溯对比
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product_fba_fee` (
  `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `product_id`            BIGINT UNSIGNED  NOT NULL                COMMENT '关联 product.id，唯一（1:1）',

  /* --- 阶段一：录入产品时写入（仅依赖物理属性 + 全局参数） --- */
  `inbound_config_fee_usd` DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '入库配置费 $/件（Excel AM），源自 sys_config INBOUND_CONFIG_FEE_USD',
  `monthly_storage_usd`    DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '月度仓储费 $/件（Excel AN）= 体积cm³ ÷ 28316.85 × 仓储费率 × 周转月数',

  /* --- 阶段二：利润测算后回填（依赖售价 / 方案） --- */
  `fba_fee_usd`           DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '尾程配送费 $/件，已含 3.5% 燃油附加',
  `storage_fee_usd`       DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '合仓费 $/件，手工输入（注意：非入库配置费）',
  `commission_usd`        DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '佣金 $/件，支持阶梯累进',
  `avg_return_cost_cny`  DECIMAL(12,4)   NULL     DEFAULT NULL   COMMENT '平均退货费用 ￥/件',
  `fba_total_cny`         DECIMAL(12,4)   NULL     DEFAULT NULL   COMMENT 'FBA 总费用 ￥/件',

  /* --- 口径标注：避免「这佣金是按哪个售价算的」说不清 --- */
  `based_price_usd`       DECIMAL(10,2)   NULL     DEFAULT NULL   COMMENT '上述依赖售价的费用是基于哪个售价算出的',
  `calc_at`               DATETIME(3)      NULL     DEFAULT NULL   COMMENT '最后一次测算时间',

  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`            TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品FBA费用（1:1；录入阶段填前两项，测算后回填其余）';
