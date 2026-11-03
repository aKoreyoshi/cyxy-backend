-- =====================================================================
-- CYXY 系统 · 数据库建表脚本
-- 目标库：cyxy        MySQL 8.0        字符集：utf8mb4 / utf8mb4_0900_ai_ci
-- 版本：2026-10-30
-- 说明：只建表不导数据。所有 CREATE 均带 IF NOT EXISTS，可重复执行。
--       字段说明见同目录 schema-fields.md
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `cyxy`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `cyxy`;

-- =====================================================================
-- 一、字典层
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 佣金类目字典（46 项：flat 33 / full 4 / marginal 9）
--    同时作为「产品录入类目下拉框」的数据源 —— 单一真源
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_category_commission` (
  `category_code`       VARCHAR(64)      NOT NULL                COMMENT '主键：业务编码，唯一',
  `category_name`       VARCHAR(128)     NOT NULL                COMMENT '类目中文名',
  `fee_mode`            VARCHAR(16)      NOT NULL DEFAULT 'FLAT' COMMENT '费率模式：FLAT单一费率 / FULL跳档整套按新费率 / MARGINAL累进分段',
  `flat_rate`           DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '单一费率（fee_mode=FLAT 时有效），如 0.1500 表示 15%',
  `min_commission_usd`  DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '最低佣金 $，NULL 表示无下限',
  `need_approval`       TINYINT          NOT NULL DEFAULT 0      COMMENT '是否需要亚马逊类目审核：0否 1是',
  `remark`              VARCHAR(512)     NULL     DEFAULT NULL   COMMENT '费率说明原文',
  `sort_no`             INT              NOT NULL DEFAULT 0      COMMENT '排序号',
  `status`              TINYINT          NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
  `valid_from`          DATE             NULL     DEFAULT NULL   COMMENT '生效起始日',
  `valid_to`            DATE             NULL     DEFAULT NULL   COMMENT '生效截止日，NULL 表示长期有效',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`category_code`),
  KEY `idx_status` (`status`),
  KEY `idx_valid` (`valid_from`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='佣金类目字典';


-- ---------------------------------------------------------------------
-- 2. 阶梯佣金字典（Excel 未实现，新做）
--    配合 fee_mode = FULL / MARGINAL 的类目使用
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_category_commission_tier` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `category_code`       VARCHAR(64)      NOT NULL                COMMENT '外键 → dict_category_commission.category_code',
  `tier_no`             INT              NOT NULL DEFAULT 1      COMMENT '档位序号',
  `price_min_usd`       DECIMAL(12,2)    NOT NULL DEFAULT 0.00   COMMENT '该档价格下界（含）',
  `price_max_usd`       DECIMAL(12,2)    NULL     DEFAULT NULL   COMMENT '该档价格上界（含），NULL 表示无上限',
  `tier_rate`           DECIMAL(6,4)     NOT NULL                COMMENT '该档费率',
  `min_commission_usd`  DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '该档最低佣金 $',
  `remark`              VARCHAR(255)     NULL     DEFAULT NULL   COMMENT '档位说明',
  `sort_no`             INT              NOT NULL DEFAULT 0      COMMENT '排序号',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cat_tier` (`category_code`, `tier_no`),
  KEY `idx_category` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶梯佣金字典';


-- ---------------------------------------------------------------------
-- 3. 类目退货阈值字典（33 项）
--    与佣金类目不做物理关联（两套官方字典命名不一致），由方案手选
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_category_return_threshold` (
  `threshold_code`      VARCHAR(64)      NOT NULL                COMMENT '主键：业务编码',
  `category_name`       VARCHAR(128)     NOT NULL                COMMENT '类目名称',
  `threshold_rate`      DECIMAL(8,6)     NOT NULL                COMMENT '退货阈值，如 0.113000 表示 11.3%',
  `remark`              VARCHAR(512)     NULL     DEFAULT NULL   COMMENT '说明',
  `sort_no`             INT              NOT NULL DEFAULT 0      COMMENT '排序号',
  `status`              TINYINT          NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
  `valid_from`          DATE             NULL     DEFAULT NULL   COMMENT '生效起始日',
  `valid_to`            DATE             NULL     DEFAULT NULL   COMMENT '生效截止日',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`threshold_code`),
  KEY `idx_status` (`status`),
  KEY `idx_valid` (`valid_from`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='类目退货阈值字典';


-- ---------------------------------------------------------------------
-- 4. FBA 尺寸分段规则
--    注意 weight_basis：分段判定用实重，查费率时小号用实重、大号与大件用计费重
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_fba_size_tier` (
  `tier_code`           VARCHAR(32)      NOT NULL                COMMENT '主键：small / large / oversize',
  `tier_name`           VARCHAR(64)      NOT NULL                COMMENT '分段中文名',
  `sort_no`             INT              NOT NULL DEFAULT 0      COMMENT '判定顺序：小号 → 大号 → 兜底大件',
  `max_length_inch`     DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '最长边上限 inch，NULL 表示不限',
  `max_width_inch`      DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '次长边上限 inch',
  `max_height_inch`     DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '最短边上限 inch',
  `max_weight_lb`       DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '重量上限 lb',
  `weight_basis`        VARCHAR(16)      NOT NULL DEFAULT 'ACTUAL' COMMENT '查费率时取值：ACTUAL 按实重 / BILLABLE 按计费重',
  `remark`              VARCHAR(256)     NULL     DEFAULT NULL   COMMENT '备注',
  `valid_from`          DATE             NULL     DEFAULT NULL   COMMENT '生效起始日',
  `valid_to`            DATE             NULL     DEFAULT NULL   COMMENT '生效截止日',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`tier_code`),
  KEY `idx_valid` (`valid_from`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='FBA尺寸分段规则';


-- ---------------------------------------------------------------------
-- 5. FBA 配送费率矩阵（行式化，共 60 行）
--    小号标准 8 档 + 大号标准 12 档，各 × 3 个价格带
--    绝不要改成 low/mid/high 三列宽表写法，亚马逊加价格带时会改不动
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_fba_rate` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `tier_code`           VARCHAR(32)      NOT NULL                COMMENT '外键 → dict_fba_size_tier.tier_code',
  `price_band`          VARCHAR(16)      NOT NULL                COMMENT '价格带：LOW(<$10) / MID($10~50) / HIGH(>$50)',
  `weight_min_oz`       DECIMAL(10,4)    NOT NULL DEFAULT 0.0000 COMMENT '重量下界 oz（左开）',
  `weight_max_oz`       DECIMAL(10,4)    NOT NULL                COMMENT '重量上界 oz（右闭）',
  `rate_usd`            DECIMAL(10,2)    NOT NULL                COMMENT '配送费 $/件，不含燃油附加',
  `valid_from`          DATE             NULL     DEFAULT NULL   COMMENT '生效起始日',
  `valid_to`            DATE             NULL     DEFAULT NULL   COMMENT '生效截止日',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tier_band_oz` (`tier_code`, `price_band`, `weight_max_oz`),
  KEY `idx_lookup` (`tier_code`, `price_band`, `weight_min_oz`, `weight_max_oz`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='FBA配送费率矩阵（行式化）';


-- ---------------------------------------------------------------------
-- 6. FBA 阶梯步进与大件费率
--    STEP_4OZ        大号标准超过 48oz(3lb) 后每 4oz 累加
--    FIRST_POUND_PLUS 大件/超大件：首磅费 + 续磅费
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_fba_step_rate` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `tier_code`           VARCHAR(32)      NOT NULL                COMMENT '外键 → dict_fba_size_tier.tier_code',
  `price_band`          VARCHAR(16)      NOT NULL                COMMENT '价格带 LOW / MID / HIGH',
  `rate_type`           VARCHAR(24)      NOT NULL                COMMENT '计费类型：STEP_4OZ 每4盎司步进 / FIRST_POUND_PLUS 首磅加续磅',
  `apply_from_lb`       DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '适用重量起点 lb',
  `apply_to_lb`         DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '适用重量终点 lb，NULL 表示无上限',
  `base_usd`            DECIMAL(10,2)    NOT NULL DEFAULT 0.00   COMMENT '起步价 $',
  `step_usd`            DECIMAL(10,4)    NOT NULL DEFAULT 0.0000 COMMENT '每步进单位加价 $',
  `step_unit`           VARCHAR(16)      NOT NULL DEFAULT 'LB'   COMMENT '步进单位：OZ4 每4盎司 / LB 每磅',
  `remark`              VARCHAR(256)     NULL     DEFAULT NULL   COMMENT '备注',
  `valid_from`          DATE             NULL     DEFAULT NULL   COMMENT '生效起始日',
  `valid_to`            DATE             NULL     DEFAULT NULL   COMMENT '生效截止日',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tier_band_type` (`tier_code`, `price_band`, `rate_type`),
  KEY `idx_lookup` (`tier_code`, `price_band`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='FBA阶梯步进与大件费率';


-- ---------------------------------------------------------------------
-- 7. 头程方式与默认单价
--    材积比挂在运输方式上：海运泡货 5000、空运 6000，不该让用户手填
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_first_leg_method` (
  `method_code`         VARCHAR(32)      NOT NULL                COMMENT '主键：SEA / SEA_EXPRESS / AIR / EXPRESS',
  `method_name`         VARCHAR(64)      NOT NULL                COMMENT '方式名称：海运 / 海运快线 / 空运 / 快递',
  `default_unit_price`  DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '默认头程单价 ￥/kg',
  `volume_divisor`      INT UNSIGNED     NULL     DEFAULT NULL   COMMENT '材积比：长×宽×高(cm) ÷ 此值 = 抛货重量 kg',
  `sort_no`             INT              NOT NULL DEFAULT 0      COMMENT '排序号',
  `status`              TINYINT          NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
  `remark`              VARCHAR(256)     NULL     DEFAULT NULL   COMMENT '备注',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`method_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='头程方式与默认单价';


-- ---------------------------------------------------------------------
-- 8. 汇率（时序表）
--    单独建表而非放 sys_config：需要保留历史画趋势曲线
--    写入策略建议：一天一条，同一天重复获取则 UPDATE 覆盖
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict_exchange_rate` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `currency_pair`       VARCHAR(16)      NOT NULL DEFAULT 'USD/CNY' COMMENT '货币对，默认 USD/CNY',
  `rate`                DECIMAL(12,6)    NOT NULL                COMMENT '汇率值（decimal，不用 float）',
  `source`              VARCHAR(16)      NOT NULL DEFAULT 'API'  COMMENT '来源：API 接口拉取 / MANUAL 手工录入',
  `provider`            VARCHAR(32)      NULL     DEFAULT NULL   COMMENT '具体数据提供方，如 frankfurter',
  `rate_date`           DATE             NOT NULL                COMMENT '业务日期，与 currency_pair 组成唯一键',
  `fetched_at`          DATETIME(3)      NULL     DEFAULT NULL   COMMENT '实际获取时间戳',
  `remark`              VARCHAR(256)     NULL     DEFAULT NULL   COMMENT '备注',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pair_date` (`currency_pair`, `rate_date`),
  KEY `idx_rate_date` (`rate_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汇率时序表';


-- ---------------------------------------------------------------------
-- 9. 全局计算参数
--    兜底存放原 Excel 里散落的硬编码常量
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_key`          VARCHAR(64)      NOT NULL                COMMENT '主键，如 FUEL_SURCHARGE_RATE',
  `config_value`        VARCHAR(256)     NULL     DEFAULT NULL   COMMENT '配置值，统一字符串存，按 value_type 转换',
  `value_type`          VARCHAR(16)      NOT NULL DEFAULT 'STRING' COMMENT '值类型：NUMBER / STRING / BOOLEAN',
  `config_name`         VARCHAR(128)     NULL     DEFAULT NULL   COMMENT '配置中文名',
  `remark`              VARCHAR(256)     NULL     DEFAULT NULL   COMMENT '说明',
  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局计算参数';


-- =====================================================================
-- 二、主数据层
-- =====================================================================

-- ---------------------------------------------------------------------
-- 10. 产品主数据
--     只装「这个东西是什么」的固有属性
--     不装：落地成本/关税/仓储/净利/保本售价/ROI 等 Excel 测算产物
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product` (
  `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `product_no`            VARCHAR(32)      NOT NULL                COMMENT '产品编号，唯一',
  `name_cn`               VARCHAR(128)     NULL     DEFAULT NULL   COMMENT '产品名称（中文）',
  `name_en`               VARCHAR(255)     NULL     DEFAULT NULL   COMMENT '产品名称（英文）',
  `main_image`            VARCHAR(512)     NULL     DEFAULT NULL   COMMENT '产品主图 URL 或对象存储 Key',
  `category_code`         VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '产品类目编码 → dict_category_return_threshold.threshold_code（录入下拉同源）；与算利润用的 dict_category_commission 互不联动',
  `competitor_asin`       VARCHAR(16)      NULL     DEFAULT NULL   COMMENT '竞品 ASIN',
  `source_url`            VARCHAR(512)     NULL     DEFAULT NULL   COMMENT '1688 采购链接',
  `supplier`              VARCHAR(128)     NULL     DEFAULT NULL   COMMENT '供应商名称',
  `purchase_price`        DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '采购单价 ￥/件（裸价，不含运费包材） Excel I',
  `moq`                   INT UNSIGNED     NULL     DEFAULT NULL   COMMENT '起订量（件） Excel J',
  `package_cost`          DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '包材/贴标 ￥/件 Excel K',
  `avg_domestic_shipping` DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '平均运费 ￥/件 Excel L',
  `net_weight_g`          DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '净重 g Excel M',
  `packaged_weight_g`     DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '含包装实重 g Excel N ★联动带入此列',
  `tariff_fee`            DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '关税费￥/件，当前手工填写，后续可改自动计算',
  `length_cm`             DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '包装长 cm Excel O',
  `width_cm`              DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '包装宽 cm Excel P',
  `height_cm`             DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '包装高 cm Excel Q',
  `size_tier_code`        VARCHAR(32)      NULL     DEFAULT NULL   COMMENT '尺寸分段 small/large/oversize → dict_fba_size_tier；由长宽高与实重自动匹配，后台保存时兜底重算',
  `first_leg_method_code` VARCHAR(32)      NULL     DEFAULT NULL   COMMENT '头程方式 → dict_first_leg_method',
  `shipping_method`       VARCHAR(32)      NULL     DEFAULT NULL   COMMENT '配送方式：AMAZON_SEND=亚马逊SEND / SELF_CARRIER=自选承运人；NULL 视为 SELF_CARRIER（历史数据）',
  `logistics_provider`    VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '物流商名称 Excel Y',
  `first_leg_unit_price`  DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '头程单价 ￥/kg Excel Z',
  `shipping_total_fee_usd` DECIMAL(12,4)   NULL     DEFAULT NULL   COMMENT '配送总费用 $（整票货件，亚马逊 SEND 模式由亚马逊后台核算；自选承运人模式为 NULL）',
  `total_units`           INT UNSIGNED     NULL     DEFAULT NULL   COMMENT '商品总数（件），本票货件总件数',

  /* --- 配送状态：预计送达 → 确认送达（按整票，一期不拆多票货件） ---
     判定口径：实际送达日期 − 预计送达日期，按自然日比较（同一天算准时）；
     时间统一 Asia/Shanghai，由应用层显式指定，不依赖 JVM 默认时区。
     「是否已确认」= actual_delivery_time 是否为空，不额外设 is_locked；
     确认后 expected_delivery_date 锁定，改需先走「撤销确认」。           */
  `expected_delivery_date` DATE             NULL     DEFAULT NULL   COMMENT '预计送达日期（自然日），录入或编辑时填写；确认送达后锁定不可改',
  `actual_delivery_time`   DATETIME(3)      NULL     DEFAULT NULL   COMMENT '实际送达时间，点确认送达时由服务端按北京时间生成；非空即视为已确认，锁定配送相关字段',
  `delivery_status`        VARCHAR(16)      NULL     DEFAULT NULL   COMMENT '配送状态：NOT_SET=未填写 / IN_TRANSIT=配送中 / EARLY=提前送达 / ON_TIME=准时送达 / LATE=晚送达',
  `delay_days`             INT              NULL     DEFAULT NULL   COMMENT '送达天数差 = 实际送达日期 - 预计送达日期（自然日）；负数提前、0 准时、正数晚几天',

  `list_price_usd`        DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '定价销售价 $/件 Excel AG',
  `competitor_price_usd`  DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '竞品售价 $ Excel AF',
  `monthly_sales_est`     INT UNSIGNED     NULL     DEFAULT NULL   COMMENT '预估月销量（件） Excel AW',
  `first_batch_qty`       INT UNSIGNED     NULL     DEFAULT NULL   COMMENT '首批备货量（件） Excel AX',
  `status`                VARCHAR(16)      NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 待评估 / SELECTED 已选 / OFFLINE 已下架',
  `remark`                VARCHAR(512)     NULL     DEFAULT NULL   COMMENT '备注 / 风险 Excel BD',
  `created_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_no` (`product_no`),
  KEY `idx_category` (`category_code`),
  KEY `idx_status` (`status`),
  KEY `idx_delivery_status` (`delivery_status`),
  KEY `idx_name_cn` (`name_cn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品主数据';


-- ---------------------------------------------------------------------
-- 10.1 产品 FBA 费用（与 product 一一对应）
--      设立目的：把「费用类」字段从产品固有属性里剥离，product 只描述"这个东西是什么"。
--
--      填充分两个阶段，均为「部分更新」，不整条覆盖：
--        阶段一 录入产品：只写 inbound_config_fee_usd / monthly_storage_usd
--                —— 只依赖「包装体积 + sys_config 全局参数」，录入时即可确定，其余留空
--        阶段二 利润测算：回填 fba_fee_usd / storage_fee_usd / commission_usd /
--                         avg_return_cost_cny / fba_total_cny
--                —— 依赖售价（佣金、平均退货费用）或方案输入（合仓费），测算后才有值
--
--      与 profit_calc_result 的分工（不是重复）：
--        product_fba_fee     产品级「当前费用速览」：1:1，可被反复部分更新，反映最新状态
--        profit_calc_result  方案级「历史快照」：每条写入后不可变，用于多轮方案回溯对比
--
--      口径标注：based_price_usd 记录依赖售价的费用是基于哪个售价算出的；
--                定价变更后若未重新测算，页面可据此提示"费用待更新"。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product_fba_fee` (
  `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `product_id`            BIGINT UNSIGNED  NOT NULL                COMMENT '关联 product.id，唯一（1:1）',

  /* --- 阶段一：录入产品时写入（仅依赖物理属性 + 全局参数） --- */
  `inbound_config_fee_usd` DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '入库配置费 $/件（Excel AM），默认取 sys_config INBOUND_CONFIG_FEE_USD',
  `monthly_storage_usd`    DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '月度仓储费 $/件（Excel AN）= 体积cm³ ÷ 28316.85 × 仓储费率 × 周转月数',

  /* --- 阶段二：利润测算后回填（依赖售价 / 方案） --- */
  `fba_fee_usd`           DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '尾程配送费 $/件，已含 3.5% 燃油附加',
  `storage_fee_usd`       DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '合仓费 $/件，手工输入（注意：与入库配置费是两笔不同费用）',
  `commission_usd`        DECIMAL(12,6)   NULL     DEFAULT NULL   COMMENT '佣金 $/件，支持阶梯累进',
  `avg_return_cost_cny`  DECIMAL(12,4)   NULL     DEFAULT NULL   COMMENT '平均退货费用 ￥/件',
  `fba_total_cny`         DECIMAL(12,4)   NULL     DEFAULT NULL   COMMENT 'FBA 总费用 ￥/件',

  /* --- 口径标注 --- */
  `based_price_usd`       DECIMAL(10,2)   NULL     DEFAULT NULL   COMMENT '上述依赖售价的费用是基于哪个售价算出的',
  `calc_at`               DATETIME(3)      NULL     DEFAULT NULL   COMMENT '最后一次测算时间；为空表示尚未测算',

  `created_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`            TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品FBA费用（1:1；录入阶段填前两项，测算后回填其余）';


-- ---------------------------------------------------------------------
-- 11. 测算方案
--     每轮「怎么卖」的输入快照。一个产品可对应多个方案；
--     product_id 可空以支持纯手填。产品信息变动不影响历史方案复算。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `profit_plan` (
  `id`                      BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `plan_no`                 VARCHAR(32)      NOT NULL                COMMENT '方案编号，唯一',
  `plan_name`               VARCHAR(128)     NULL     DEFAULT NULL   COMMENT '方案名称',
  `product_id`              BIGINT UNSIGNED  NULL     DEFAULT NULL   COMMENT '关联产品 product.id，可空（纯手填方案）',
  `source`                  VARCHAR(16)      NOT NULL DEFAULT 'MANUAL' COMMENT '来源：PRODUCT 选品带入 / MANUAL 纯手填',
  `size_unit`               VARCHAR(8)       NOT NULL DEFAULT 'cm'   COMMENT '尺寸单位 cm / inch',
  `length`                  DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '最长边（用户填的原值，未换算）',
  `width`                   DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '次长边',
  `height`                  DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '最短边',
  `weight_kg`               DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '产品重量 kg',
  `purchase_cost`           DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '采购/生产成本（含运费）￥',
  `ad_rate`                 DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '广告费用占比 %（15 表示 15%）',
  `vat_rate`                DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT 'VAT 占比 %',
  `misc_rate`               DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '其他杂费占比 %',
  `volume_divisor`          INT UNSIGNED     NULL     DEFAULT NULL   COMMENT '材积比（抛货），默认由头程方式带出，允许覆盖',
  `logistics_provider`      VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '物流商',
  `first_leg_price`         DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '头程运费单价 ￥/kg',
  `commission_category`     VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '佣金类目 → dict_category_commission',
  `return_category`         VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '退货阈值类目 → dict_category_return_threshold（不联动，每轮手选）',
  `return_rate`             DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '退货率 %',
  `return_fee_usd`          DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '尾程退货费 $/件',
  `storage_fee_usd`         DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '合仓费 $/件（页面标签「合仓费」，字段名 storage）',
  `sale_price_usd`          DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '售价 $',
  `exchange_rate`           DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '汇率快照（本方案采用，不随实时汇率变动）',
  `target_margin`           DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '目标利润率 %',
  `use_min_commission`      TINYINT          NOT NULL DEFAULT 0      COMMENT '是否取最低佣金：0否 1是',
  `remark`                  VARCHAR(512)     NULL     DEFAULT NULL   COMMENT '方案备注',
  `created_time`              DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`              DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_no` (`plan_no`),
  KEY `idx_product` (`product_id`),
  KEY `idx_source` (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='利润测算方案';


-- ---------------------------------------------------------------------
-- 12. 计算结果快照（全列冗余，不存 JSON）
--     字段一一对应前端 calc.js 的 calculate() 返回值，便于 Java 迁移回归对照
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `profit_calc_result` (
  `id`                        BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `plan_id`                   BIGINT UNSIGNED  NULL     DEFAULT NULL   COMMENT '所属方案 profit_plan.id',
  `product_id`                BIGINT UNSIGNED  NULL     DEFAULT NULL   COMMENT '关联产品（冗余，便于按产品查历史）',
  `calc_at`                   DATETIME(3)      NULL     DEFAULT NULL   COMMENT '计算时间',
  `dict_version`              VARCHAR(32)      NULL     DEFAULT NULL   COMMENT '命中的字典版本/生效日，便于追溯',
  `size_cm_l`                 DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '规范化后长度 cm',
  `size_cm_w`                 DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '规范化后宽度 cm',
  `size_cm_h`                 DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '规范化后高度 cm',
  `size_inch_l`               DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '换算后长度 inch',
  `size_inch_w`               DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '换算后宽度 inch',
  `size_inch_h`               DECIMAL(10,4)    NULL     DEFAULT NULL   COMMENT '换算后高度 inch',
  `vol_weight_kg`             DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '抛货体积重 kg（长×宽×高cm ÷ 材积比）',
  `vol_weight_lb`             DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '抛货体积重 lb（页面双单位展示）',
  `weight_lb`                 DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '产品实重 lb',
  `dim_weight_lb`             DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '体积重 lb（长×宽×高in ÷ 139）',
  `bill_weight_lb`            DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '计费重 lb（实重与体积重取大）',
  `bill_weight_basis`         VARCHAR(16)      NULL     DEFAULT NULL   COMMENT '计费依据：actual 按实重 / dimensional 按体积重',
  `first_leg_cost_cny`        DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '头程运费 ￥',
  `size_tier_key`             VARCHAR(32)      NULL     DEFAULT NULL   COMMENT '尺寸分段：small / large / oversize',
  `size_tier_label`           VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '尺寸分段中文名',
  `price_band`                VARCHAR(16)      NULL     DEFAULT NULL   COMMENT '价格带：low / mid / high',
  `price_band_label`          VARCHAR(32)      NULL     DEFAULT NULL   COMMENT '价格带显示文案',
  `fba_fee_before_fuel_usd`   DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '尾程配送费 $（未含燃油附加）',
  `fba_fee_usd`               DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '尾程配送费 $（已含 3.5% 燃油附加）',
  `commission_category_code`  VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '命中的佣金类目编码',
  `commission_rate`           DECIMAL(8,6)     NULL     DEFAULT NULL   COMMENT '实际佣金率',
  `commission_amount_usd`     DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '佣金金额 $（阶梯后改用金额口径）',
  `return_threshold`          DECIMAL(8,6)     NULL     DEFAULT NULL   COMMENT '类目退货阈值',
  `return_rate`               DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '退货率',
  `threshold_diff`            DECIMAL(8,6)     NULL     DEFAULT NULL   COMMENT '退货阈值差 = MAX(0, 退货率 − 阈值)',
  `return_cost_cny`           DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '平均退货费用 ￥',
  `purchase_cost`             DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '采购成本 ￥（输入冗余）',
  `storage_fee_usd`           DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '合仓费 $（输入冗余）',
  `return_fee_usd`            DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '尾程退货费 $（输入冗余）',
  `ad_rate`                   DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '广告占比 %（输入冗余）',
  `vat_rate`                  DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT 'VAT 占比 %（输入冗余）',
  `misc_rate`                 DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '其他杂费占比 %（输入冗余）',
  `exchange_rate`             DECIMAL(12,6)    NULL     DEFAULT NULL   COMMENT '本次计算所用汇率 ★必须固化',
  `sale_price_usd`            DECIMAL(10,2)    NULL     DEFAULT NULL   COMMENT '售价 $（输入冗余）',
  `revenue_cny`               DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '到手收入 ￥',
  `total_cost_cny`            DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '产品总成本 ￥',
  `advance_cost_cny`          DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '实际垫资成本 ￥',
  `fba_total_cny`             DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT 'FBA 费用合计 ￥',
  `profit_cny`                DECIMAL(12,4)    NULL     DEFAULT NULL   COMMENT '利润 ￥',
  `margin_rate`               DECIMAL(10,6)    NULL     DEFAULT NULL   COMMENT '利润率（0~1 小数，页面乘 100 展示）',
  `target_margin`             DECIMAL(6,4)     NULL     DEFAULT NULL   COMMENT '目标利润率 %',
  `margin_status`             VARCHAR(16)      NULL     DEFAULT NULL   COMMENT '判定：good / warn / danger',
  `created_time`                DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`                DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0                     COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  KEY `idx_plan` (`plan_id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_calc_at` (`calc_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='利润计算结果快照';


SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 建表完成。下一步可选择性导入字典数据：见 data-dict.sql（尚未生成）
-- =====================================================================
