# CYXY 数据库字段清单

> 版本：2026-10-30
> 目标库：MySQL 8.0，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`
> 共 **12 张表**：主数据层 3 张 + 字典层 9 张
> 数据来源口径：字段设计依据前端已验证的 `frontend/v3s/src/views/tools/profit-calculator/dict.js` 与 `calc.js`

---

## 通用约定

**1. 每张表必带以下四个公共字段**（为节省篇幅，下方各表不再重复列出）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint unsigned | 物理主键，自增。**仅业务表使用**；字典表用业务编码字符串作主键 |
| `created_time` | datetime(3) | 创建时间，默认 `CURRENT_TIMESTAMP(3)` |
| `updated_time` | datetime(3) | 更新时间，`ON UPDATE CURRENT_TIMESTAMP(3)` |
| `is_deleted` | tinyint | 软删标记，`0` 未删除 / `1` 已删除（默认 0，配合 `@TableLogic`） |

> ⚠️ **历史修正**：早期文档曾写作 `created_at` / `updated_at` / `deleted_at(datetime)`，
> 与真实库不一致。真实库一律为 `created_time` / `updated_time` / `is_deleted(tinyint)`，
> 本文档已同步。Entity 侧通过 `BaseEntity` 统一映射，无需逐个改。

**2. 金额一律用 `decimal`**，禁止 `float` / `double`（浮点做金额会产生不可察觉的误差）。

**3. 字典表主键用业务编码**（如 `size_tier_code = 'small_standard'`），不用自增 ID —— 本地/测试/生产来回导数据时自增 ID 会错位，业务编码不会。

**4. 所有字典表带 `valid_from` / `valid_to`** —— FBA 费率一年调两次，没有生效期就无法复算历史方案。

**5. 字典数据应用层一次全量载入内存建 Map**，不在查询里 JOIN 字典表。

---

# 一、主数据层

## 1. `product` 产品主数据

对应 Excel 选品总表 A~BD 列中的「固有属性」部分。**金额/重量/尺寸/供应商**这类属于"这个东西是什么"的字段才入此表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `product_no` | varchar(32) | 产品编号，唯一索引 |
| `name_cn` | varchar(128) | 产品名称（中文） |
| `name_en` | varchar(255) | 产品名称（英文） |
| `main_image` | varchar(512) | 产品主图 URL 或对象存储 Key |
| `category_code` | varchar(64) | **产品类目编码**，外键 → `dict_category_return_threshold.threshold_code`，即产品录入下拉的数据源。**与算利润用的 `dict_category_commission` 互不联动**（两套字典独立） |
| `competitor_asin` | varchar(16) | 竞品 ASIN |
| `source_url` | varchar(512) | 1688 采购链接 |
| `supplier` | varchar(128) | 供应商名称 |
| `purchase_price` | decimal(12,4) | 采购单价 ￥/件（**裸价，不含运费包材**）（Excel I 列） |
| `moq` | int unsigned | 起订量（件）（J 列） |
| `package_cost` | decimal(12,4) | 包材/贴标 ￥/件（K 列） |
| `avg_domestic_shipping` | decimal(12,4) | 平均运费 ￥/件（L 列） |
| `net_weight_g` | decimal(10,2) | 净重 g（M 列） |
| `packaged_weight_g` | decimal(10,2) | **含包装实重 g**（N 列）★ 联动带入此列而非净重 |
| `tariff_fee` | decimal(12,4) | **关税费 ￥/件**，当前**手工填写**；后续可改为 `(采购单价 + 包材) × 关税率` 自动计算 |
| `length_cm` | decimal(10,2) | 包装长 cm（O 列） |
| `width_cm` | decimal(10,2) | 包装宽 cm（P 列） |
| `height_cm` | decimal(10,2) | 包装高 cm（Q 列） |
| `size_tier_code` | varchar(32) | **尺寸分段**：`small` / `large` / `oversize`，外键 → `dict_fba_size_tier`。由「包装长/宽/高 + 含包装实重」**自动匹配**得出，不需人工选择；后台保存时按字典规则兜底重算 |
| `first_leg_method_code` | varchar(32) | 头程方式，外键 → `dict_first_leg_method` |
| `shipping_method` | varchar(32) | **配送方式**：`AMAZON_SEND` 亚马逊SEND（配送费按当前产品核算，填 `shipping_total_fee_usd`，不产生运费单价）/ `SELF_CARRIER` 自己选择承运人（填 `first_leg_unit_price` + `shipping_total_fee_cny`）。`NULL` 视为 `SELF_CARRIER`（历史数据兼容） |
| `logistics_provider` | varchar(64) | 物流商名称（Y 列） |
| `first_leg_unit_price` | decimal(10,2) | 头程单价 ￥/kg（Z 列）。**仅 SELF_CARRIER 模式使用** |
| `shipping_total_fee_usd` | decimal(12,4) | **配送总费用 $（当前产品）**。**仅 AMAZON_SEND 模式有值**，一行只记一种产品的费用，**不再代表整票货件** |
| `total_units` | int unsigned | **商品总数量（件，当前产品）**。每行记录单种产品，不记单票货件总数；两种配送方式都填 |
| `shipping_total_fee_cny` | decimal(12,4) | **配送总费用 ￥（当前产品）**。**仅 SELF_CARRIER 模式填写**，与「头程单价」同组；`AMAZON_SEND` 模式为 `NULL`。本期只做录入与留存，不参与利润计算 |

> ⚠️ **两个「配送总费用」按配送方式严格二选一，绝不混用、也不互相换算**：
>
> | 配送方式 | 填哪个费用字段 | 同组字段 |
> | --- | --- | --- |
> | `AMAZON_SEND` 亚马逊SEND | `shipping_total_fee_usd`（$） | `total_units` |
> | `SELF_CARRIER` 自己选择承运人 | `shipping_total_fee_cny`（￥） | `first_leg_unit_price`（头程单价）、`total_units` |
>
> 列名后缀 `usd` / `cny` 是第一层区分，币种不同、来源也不同（亚马逊后台核算 vs 自己找物流商结算），
> 所以不合并成一个字段；表单按配送方式条件显示，同一时刻只会看到其中一个。
| `expected_delivery_date` | date | **预计送达日期**（自然日，精确到天）。录入或编辑时填写；**确认送达后锁定**，需改先撤销确认 |
| `actual_delivery_time` | datetime(3) | **实际送达时间**，点「确认送达」时由服务端按北京时间生成。**非空 = 已确认**，锁定配送相关字段；撤销确认时清空 |
| `delivery_status` | varchar(16) | **配送状态**：`NOT_SET` 未填写 / `IN_TRANSIT` 配送中 / `EARLY` 提前送达 / `ON_TIME` 准时送达 / `LATE` 晚送达。冗余字段，便于列表筛选排序，判定真值仍看两个时间 |
| `delay_days` | int | **送达天数差** = 实际送达日期 − 预计送达日期（自然日）。负数=提前几天，0=准时，正数=晚几天 |
| `list_price_usd` | decimal(10,2) | 定价销售价 $/件（AG 列） |
| `competitor_price_usd` | decimal(10,2) | 竞品售价 $（AF 列） |
| `monthly_sales_est` | int unsigned | 预估月销量（件）（AW 列） |
| `first_batch_qty` | int unsigned | 首批备货量（件）（AX 列） |
| `status` | varchar(16) | 产品状态：`DRAFT` 待评估 / `SELECTED` 已选 / `OFFLINE` 已下架 |
| `remark` | varchar(512) | 备注 / 风险（BD 列） |

> **明确不入库的字段**：体积 cm³、实重 lb、体积重 lb、计费重 lb/oz（纯中间计算值，随单位换算而变）；
> 落地成本、销售佣金、FBA 配送费、仓储费、单件净利、净利率、保本售价、达标售价、月 ROI、ABCD 结论评级
> （Excel 那套算法的产物，联动后由系统重算，落在 `profit_calc_result` / `product_fba_fee`）。
>
> 📌 **变更（2026-11-01）**：`size_tier_code` 原被归为中间计算值不入 `product`，现已入库。
> 原因：它由物理属性唯一确定、判定规则固化在 `dict_fba_size_tier` 字典里，
> 且列表展示 / 费用查表 / 历史追溯都要用，每次临时重算反而容易各端口径不一致。
> 入库值由后台按字典规则统一计算写入，前端不直接传。
> **陷阱**：`AD 落地成本 = I+K+L+头程运费+关税`，已含头程。若用它喂给计算器会因重复计入而算错利润。

---

## 1.1 `product_fba_fee` 产品 FBA 费用（1:1）

**设立目的**：把「费用类」字段从产品固有属性里剥离出去。
`product` 只回答"这个东西是什么"，费用统一放这张表，随测算进度逐步填充。

| 字段 | 类型 | 说明 |
|---|---|---|
| `product_id` | bigint unsigned | 关联 `product.id`，**唯一索引**（一个产品一条费用记录） |
| `inbound_config_fee_usd` | decimal(12,6) | **入库配置费** $/件（Excel AM 列） |
| `monthly_storage_usd` | decimal(12,6) | **仓储费** $/件（Excel AN 列） |
| `fba_fee_usd` | decimal(12,6) | **尾程配送费** $/件，**已含** 3.5% 燃油附加 |
| `storage_fee_usd` | decimal(12,6) | **合仓费** $/件，手工输入 |
| `commission_usd` | decimal(12,6) | **佣金** $/件（支持阶梯累进） |
| `avg_return_cost_cny` | decimal(12,4) | **平均退货费用** ￥/件 |
| `fba_total_cny` | decimal(12,4) | **FBA 总费用** ￥/件 |
| `based_price_usd` | decimal(10,2) | 口径标注：上述依赖售价的费用是基于哪个售价算出的 |
| `calc_at` | datetime(3) | 最后一次测算时间；**为空表示尚未测算**（仅完成阶段一） |

### 分阶段填充规则（核心约定）

| 阶段 | 触发时机 | 写入字段 | 未写字段 |
|---|---|---|---|
| **阶段一** | 录入 / 编辑产品 | `inbound_config_fee_usd`、`monthly_storage_usd` | 其余 5 项保持 `NULL` |
| **阶段二** | 对该产品做利润测算 | `fba_fee_usd`、`storage_fee_usd`、`commission_usd`、`avg_return_cost_cny`、`fba_total_cny` + `based_price_usd`、`calc_at` | — |

**为什么能这么切**：
- `inbound_config_fee_usd` = 全局常量（sys_config 的 `INBOUND_CONFIG_FEE_USD`），跟售价无关
- `monthly_storage_usd` = 体积ft³ × 仓储费率 × 周转月数，只依赖**包装体积**
- 其余 5 项都依赖**售价**（佣金、退货费）或**方案输入**（合仓费），必须测算后才有值

**实现要点**：两次都是部分更新，用 `updateById` + MyBatis-Plus 默认的 `NOT_NULL` 字段策略即可自动跳过 `NULL` 字段，不会把阶段一的值抹掉。

### 与 `profit_calc_result` 的分工（不是重复）

| | `product_fba_fee` | `profit_calc_result` |
|---|---|---|
| 定位 | 产品级「**当前费用速览**」 | 方案级「**历史快照**」 |
| 关系 | 1 : 1（一个产品一条） | 1 : N（一个产品多轮方案） |
| 可变性 | 会被反复部分更新 | 写入后**不可变** |
| 用途 | 列表/详情直接展示最新费用 | 多轮方案回溯对比 |

> 口径提示：产品定价改动后，若 `based_price_usd` 与当前 `product.list_price_usd` 不一致，
> 页面可提示"费用基于旧售价，建议重新测算"。

---

## 2. `profit_plan` 测算方案

每轮"怎么卖"的输入快照。**一个产品可以对应多个方案**（不同售价/物流组合）；`product_id` 可空，支持完全手填。

| 字段 | 类型 | 说明 |
|---|---|---|
| `plan_no` | varchar(32) | 方案编号，唯一索引 |
| `plan_name` | varchar(128) | 方案名称，如「狗狗训练垫-秋季促销」 |
| `product_id` | bigint unsigned | 关联产品，**可空**（纯手填方案） |
| `source` | varchar(16) | 来源：`PRODUCT` 由选品带入 / `MANUAL` 纯手填 |
| `size_unit` | varchar(8) | 尺寸单位 `cm` / `inch` |
| `length` | decimal(10,2) | 最长边（用户填的原值，未换算） |
| `width` | decimal(10,2) | 次长边 |
| `height` | decimal(10,2) | 最短边 |
| `weight_kg` | decimal(10,4) | 产品重量 kg |
| `purchase_cost` | decimal(12,4) | 采购/生产成本（含运费）￥ |
| `ad_rate` | decimal(6,4) | 广告费用占比 %（如 15 表示 15%） |
| `vat_rate` | decimal(6,4) | VAT 占比 % |
| `misc_rate` | decimal(6,4) | 其他杂费占比 % |
| `volume_divisor` | int unsigned | **材积比**（抛货），默认由 `first_leg_method_code` 带出，允许覆盖 |
| `logistics_provider` | varchar(64) | 物流商 |
| `first_leg_price` | decimal(10,2) | 头程运费单价 ￥/kg（仅 SELF_CARRIER 参与计算） |
| `shipping_method` | varchar(16) | **配送方式**：AMAZON_SEND 亚马逊SEND / SELF_CARRIER 自选承运人；NULL 视为 SELF_CARRIER。决定头程算法 |
| `first_leg_method_code` | varchar(32) | 头程方式 → `dict_first_leg_method`（记录用） |
| `shipping_total_fee_usd` | decimal(12,2) | 配送总费用 $（整票货件，仅 AMAZON_SEND 参与计算） |
| `total_units` | int unsigned | 商品总数（件，仅 AMAZON_SEND 参与单件头程计算） |
| `commission_category` | varchar(64) | 佣金类目编码 → `dict_category_commission` |
| `return_category` | varchar(64) | **退货阈值类目** → `dict_category_return_threshold`。**不联动，每轮手选**（两套字典命名不一致） |
| `return_rate` | decimal(6,4) | 退货率 % |
| `return_fee_usd` | decimal(10,2) | 尾程退货费 $/件 |
| `storage_fee_usd` | decimal(10,2) | **合仓费** $/件（页面标签写"合仓费"，字段名是 storage） |
| `inbound_config_fee_usd` | decimal(12,6) | **入库配置费** $/件，计算器「FBA费用计算」卡片手填；参与利润计算，空按 0 处理 |
| `monthly_storage_usd` | decimal(12,6) | **月度仓储费** $/件，计算器「FBA费用计算」卡片手填；参与利润计算，空按 0 处理 |
| `sale_price_usd` | decimal(10,2) | 售价 $ |
| `exchange_rate` | decimal(12,6) | **汇率快照**（本方案采用的汇率，不随实时汇率变动） |
| `target_margin` | decimal(6,4) | 目标利润率 % |
| `use_min_commission` | tinyint | 是否取最低佣金（0/1） |
| `remark` | varchar(512) | 方案备注 |

---

## 3. `profit_calc_result` 计算结果快照

**全列冗余，不存 JSON**。字段一一对应 `calc.js` 的 `calculate()` 返回值，迁移 Java 时两边可直接对照做回归。

| 字段 | 类型 | 说明 |
|---|---|---|
| `plan_id` | bigint unsigned | 所属方案 |
| `product_id` | bigint unsigned | 关联产品（冗余，便于按产品查历史） |
| `calc_at` | datetime | 计算时间 |
| `dict_version` | varchar(32) | 本次计算命中的字典版本/生效日，便于追溯 |
| `size_cm_l` / `size_cm_w` / `size_cm_h` | decimal(10,2) | 规范化后的厘米尺寸 |
| `size_inch_l` / `size_inch_w` / `size_inch_h` | decimal(10,4) | 换算后的英寸尺寸 |
| `vol_weight_kg` | decimal(12,6) | 抛货体积重 kg（长×宽×高cm ÷ 材积比） |
| `vol_weight_lb` | decimal(12,6) | 抛货体积重 lb（页面双单位展示用） |
| `weight_lb` | decimal(12,6) | 产品实重 lb |
| `dim_weight_lb` | decimal(12,6) | 体积重 lb（长×宽×高in ÷ 139） |
| `bill_weight_lb` | decimal(12,6) | **计费重 lb**（实重与体积重取大） |
| `bill_weight_basis` | varchar(16) | **计费依据**：`actual` 按实重 / `dimensional` 按体积重 |
| `first_leg_cost_cny` | decimal(12,4) | 头程运费 ￥ |
| `size_tier_key` | varchar(32) | 尺寸分段：`small` / `large` / `oversize` |
| `size_tier_label` | varchar(64) | 尺寸分段中文名 |
| `price_band` | varchar(16) | 价格带：`low`(<$10) / `mid`($10~50) / `high`(>$50) |
| `price_band_label` | varchar(32) | 价格带显示文案 |
| `fba_fee_before_fuel_usd` | decimal(12,6) | 尾程配送费 $（**未含**燃油附加） |
| `fba_fee_usd` | decimal(12,6) | 尾程配送费 $（**已含** 3.5% 燃油附加） |
| `commission_category_code` | varchar(64) | 命中的佣金类目编码 |
| `commission_rate` | decimal(8,6) | 实际佣金率 |
| `commission_amount_usd` | decimal(12,6) | **佣金金额 $**（支持阶梯后改用金额口径） |
| `return_threshold` | decimal(8,6) | 类目退货阈值 |
| `return_rate` | decimal(6,4) | 退货率 |
| `threshold_diff` | decimal(8,6) | 退货阈值差 = MAX(0, 退货率 − 阈值) |
| `return_cost_cny` | decimal(12,4) | 平均退货费用 ￥ |
| `purchase_cost` | decimal(12,4) | 采购成本 ￥（输入冗余） |
| `storage_fee_usd` | decimal(10,2) | 合仓费 $（输入冗余） |
| `inbound_config_fee_usd` | decimal(12,6) | 入库配置费 $/件（输入冗余），空按 0 处理 |
| `monthly_storage_usd` | decimal(12,6) | 月度仓储费 $/件（输入冗余），空按 0 处理 |
| `return_fee_usd` | decimal(10,2) | 尾程退货费 $（输入冗余） |
| `ad_rate` / `vat_rate` / `misc_rate` | decimal(6,4) | 各项占比（输入冗余） |
| `exchange_rate` | decimal(12,6) | 本次计算所用汇率，**必须固化** |
| `sale_price_usd` | decimal(10,2) | 售价 $（输入冗余） |
| `revenue_cny` | decimal(12,4) | 到手收入 ￥ |
| `total_cost_cny` | decimal(12,4) | 产品总成本 ￥ |
| `advance_cost_cny` | decimal(12,4) | 实际垫资成本 ￥ |
| `fba_total_cny` | decimal(12,4) | FBA 费用合计 ￥ |
| `profit_cny` | decimal(12,4) | **利润 ￥** |
| `margin_rate` | decimal(10,6) | **利润率**（0~1 小数，页面乘 100 展示） |
| `target_margin` | decimal(6,4) | 目标利润率 % |
| `margin_status` | varchar(16) | 判定：`good` / `warn` / `danger` |

---

# 二、字典层

## 4. `dict_category_commission` 佣金类目（46 项）

三种费率模式：`flat` 33 项、`full` 4 项、`marginal` 9 项。

| 字段 | 类型 | 说明 |
|---|---|---|
| `category_code` | varchar(64) | **主键**（业务编码），同时作为产品录入下拉的数据源 |
| `category_name` | varchar(128) | 类目中文名 |
| `fee_mode` | varchar(16) | **费率模式**：`FLAT` 单一费率 / `FULL` 跳档（整套按新费率）/ `MARGINAL` 累进（分段计算） |
| `flat_rate` | decimal(6,4) | 单一费率（mode=FLAT 时有效），如 0.15 |
| `min_commission_usd` | decimal(10,2) | 最低佣金 $，`NULL` 表示无下限 |
| `need_approval` | tinyint | 是否需要亚马逊审核（0/1） |
| `remark` | varchar(512) | 费率说明原文，如"$100 以内部分 15%，超出部分 8%" |
| `sort_no` | int | 排序号 |
| `status` | tinyint | 1 启用 / 0 停用 |
| `valid_from` | date | 生效起始日 |
| `valid_to` | date | 生效截止日，`NULL` 表示长期有效 |

---

## 5. `dict_category_commission_tier` 阶梯佣金（Excel 未实现，新做）

配合 `fee_mode = FULL / MARGINAL` 的类目使用，每条类目若干档。

| 字段 | 类型 | 说明 |
|---|---|---|
| `category_code` | varchar(64) | 外键 → `dict_category_commission` |
| `tier_no` | int | 档位序号 |
| `price_min_usd` | decimal(12,2) | 该档价格下界（含） |
| `price_max_usd` | decimal(12,2) | 该档价格上界（含），**`NULL` 表示无上限** |
| `tier_rate` | decimal(6,4) | 该档费率 |
| `min_commission_usd` | decimal(10,2) | 该档最低佣金 $ |
| `remark` | varchar(255) | 档位说明 |
| `sort_no` | int | 排序号 |

> 样例：`电子产品配件`（MARGINAL）→ 第 1 档 0~$100 收 15%，第 2 档 >$100 部分收 8%。
> `母婴(婴儿服装除外)`（FULL）→ ≤$10 收 8%，超出后整套按 15%。

---

## 6. `dict_category_return_threshold` 类目退货阈值（33 项）

| 字段 | 类型 | 说明 |
|---|---|---|
| `threshold_code` | varchar(64) | **主键**（业务编码） |
| `category_name` | varchar(128) | 类目名称 |
| `threshold_rate` | decimal(8,6) | 退货阈值，如 0.113 表示 11.3% |
| `remark` | varchar(512) | 说明 |
| `sort_no` | int | 排序号 |
| `status` | tinyint | 1 启用 / 0 停用 |
| `valid_from` | date | 生效起始日 |
| `valid_to` | date | 生效截止日 |

> 本表与佣金类目**不做自动关联**（两套官方字典命名不一致），由用户在方案中手选。

---

## 7. `dict_fba_size_tier` 尺寸分段规则

| 字段 | 类型 | 说明 |
|---|---|---|
| `tier_code` | varchar(32) | **主键**：`small` / `large` / `oversize` |
| `tier_name` | varchar(64) | 小号标准尺寸 / 大号标准尺寸 / 大件超大件 |
| `sort_no` | int | 判定顺序（小号 → 大号 → 兜底大件） |
| `max_length_inch` | decimal(10,4) | 最长边上限 inch |
| `max_width_inch` | decimal(10,4) | 次长边上限 inch |
| `max_height_inch` | decimal(10,4) | 最短边上限 inch |
| `max_weight_lb` | decimal(10,4) | 重量上限 lb |
| `weight_basis` | varchar(16) | **关键**：`ACTUAL` 按实重 / `BILLABLE` 按计费重。见下方说明 |
| `remark` | varchar(256) | 备注 |
| `valid_from` | date | 生效起始日 |
| `valid_to` | date | 生效截止日 |

> ⚠️ **这个字段解决一个隐藏很深的坑**：
> - **分段判定**永远用**实重**（三条 tier 记录此列其实不影响判定逻辑）
> - **查费率时**：小号标准用**实重**，大号标准和大件用**计费重**（体积重取大后）
> 把这个口径写进表里，Java 里就不用再硬编码一遍。

当前数据：小号 ≤15×12×0.75 in 且 ≤1 lb；大号 ≤18×14×8 in 且 ≤20 lb；其余为 oversize。

---

## 8. `dict_fba_rate` FBA 配送费率矩阵（行式化，共 60 行）

原 Excel 是二维矩阵（行=重量档，列=价格带），入库时拉平成行式记录。

| 字段 | 类型 | 说明 |
|---|---|---|
| `tier_code` | varchar(32) | 外键 → `dict_fba_size_tier` |
| `price_band` | varchar(16) | 价格带：`LOW`(<$10) / `MID`($10~50) / `HIGH`(>$50) |
| `weight_min_oz` | decimal(10,4) | 重量下界 oz（**左开**，用前一档上界+0.0001） |
| `weight_max_oz` | decimal(10,4) | 重量上界 oz（**右闭**） |
| `rate_usd` | decimal(10,2) | 配送费 $/件，**不含燃油附加** |
| `valid_from` | date | 生效起始日 |
| `valid_to` | date | 生效截止日 |

**唯一索引**：`(tier_code, price_band, weight_max_oz)`

**数据量**：小号标准 8 档 × 3 价格带 + 大号标准 12 档 × 3 价格带 = **60 行**

**查询方式**：
```sql
WHERE tier_code = :tier AND price_band = :band
  AND :oz BETWEEN weight_min_oz AND weight_max_oz
```

> ⚠️ **绝不能用 `rate_low / rate_mid / rate_high` 三列的宽表写法** —— 一旦亚马逊增加第四个价格带，就要改表结构、改实体、改所有查询。

---

## 9. `dict_fba_step_rate` 阶梯步进与大件费率

覆盖两类场景：大号标准超过 48oz（3lb）后的累加步进；大件/超大件的首磅+续磅。

| 字段 | 类型 | 说明 |
|---|---|---|
| `tier_code` | varchar(32) | 外键 → `dict_fba_size_tier` |
| `price_band` | varchar(16) | 价格带 `LOW` / `MID` / `HIGH` |
| `rate_type` | varchar(24) | `STEP_4OZ` 每 4oz 步进 / `FIRST_POUND_PLUS` 首磅+续磅 |
| `apply_from_lb` | decimal(10,4) | 适用重量起始 lb |
| `apply_to_lb` | decimal(10,4) | 适用重量截止 lb，`NULL` 表示无上限 |
| `base_usd` | decimal(10,2) | 起步价 $ |
| `step_usd` | decimal(10,4) | 每步进单位加价 $ |
| `step_unit` | varchar(16) | 步进单位：`OZ4`（每 4 盎司）/ `LB`（每磅） |
| `valid_from` | date | 生效起始日 |
| `valid_to` | date | 生效截止日 |

**当前数据**：
- STEP_4OZ（大号标准 3~20lb）：base 6.69 / 7.51 / 7.77，step 0.08
- FIRST_POUND_PLUS（大件）：base 6.78，step 0.38，step_unit = LB

---

## 9.1 `dict_return_processing_fee` 尾程退货处理费

2026-11-10 新增。数据来源 `D:/Workbuddy/尾程退货费.xlsx`（原表 Sheet1 R4~R29 共 26 行）。
利润计算器的「尾程退货费」由手填改为**只按实际计费重自动带出，输入框仍可手动更改**；
带出结果只是填值，不锁定、不覆盖用户后续手改。

| 字段 | 类型 | 说明 |
|---|---|---|
| `tier_code` | varchar(32) | 大类 `small` / `large` / `oversize`，**只用于收窄范围**，不是必填条件；传空则全表按重量匹配 |
| `group_code` | varchar(32) | 分组编码，7 个业务分组；参数列表展示页按它分节 |
| `group_name` | varchar(64) | 分组名称（原表没有标题行，由业务口径给定） |
| `sort_no` | int | 同大类内匹配顺序；区间重叠时取序号最小的一行 |
| `weight_min_oz` | decimal(10,4) | 重量下界 oz（**左开**），匹配用 |
| `weight_max_oz` | decimal(10,4) | 重量上界 oz（**右闭**），匹配用；`NULL` 表示不限 |
| `weight_label` | varchar(64) | 原表 A 列区间文字，**仅展示、不参与判定** |
| `fee_type` | varchar(16) | `FLAT` 固定值 / `FORMULA` 起步价+续重 |
| `fee_usd` | decimal(10,2) | 固定费用 $/件（仅 FLAT） |
| `base_fee_usd` | decimal(10,2) | 起步价 $（仅 FORMULA） |
| `step_fee_usd` | decimal(10,4) | 每步进单位加价 $（仅 FORMULA） |
| `step_unit` | varchar(16) | `OZ4` 每 4 盎司 / `LB` 每磅 |
| `step_from_oz` | decimal(10,4) | 续重起算点 oz，只有超出这部分的重量才累加 |
| `formula_text` | varchar(256) | 原表公式文字，展示用 |
| `valid_from` / `valid_to` | date | 生效期；当前留空，见下方说明 |

**7 个业务分组**（原表 R4~R29，行数即档位数）：

| 分组 | 原表行 | 行数 | 说明 |
|---|---|---|---|
| `SMALL_STANDARD` 小号标准尺寸 | R4~R11 | 8 | 2 盎司一档，固定值 1.78~2.21 |
| `LARGE_STANDARD` 大号标准尺寸 | R12~R24 | 13 | 12 档固定值 2.36~4.73 + 3~20lb 续重档 1 行 |
| `LARGE_BULKY` 大号大件 | R25 | 1 | 0~50lb，$6.74 + 超出首磅每磅 $0.32 |
| `OVERSIZE_0_50` 超大尺寸：0-50磅 | R26 | 1 | 0~50lb，$26.33 + 超出首磅每磅 $0.38 |
| `OVERSIZE_50_70` 超大件：50-70磅(不含50磅) | R27 | 1 | $40.12 + 超出 51 磅每磅 $0.75 |
| `OVERSIZE_70_150` 超大件：70-150磅(不含70磅) | R28 | 1 | $52.45 + 超出 71 磅每磅 $0.75 |
| `OVERSIZE_150_PLUS` 超大件：150磅以上(不含150磅) | R29 | 1 | $157.35 + 超出 151 磅每磅 $0.19 |

**为什么只按计费重取值**：计算器本来就算得出计费重和尺寸分段，用户不该为了带出一个
费用再多填一项。原表重量区间互相重叠（小号/大号都有 ≤2oz、≤4oz 档，大件两行都写
「0 至 50 磅」），所以后端按 `tierRank()`（原表大类顺序）+ `sort_no` 排序后取第一条命中，
保证结果唯一；前端把已有 `sizeTierKey` 传进去只是为了收窄，不传也能查到值。

**⚠️ 大件边界按分组名，不按 A 列原文**：R27/R28 的 A 列写「0 至 70 磅」「0 至 150 磅」，
但实际匹配区间分别是 800~1120oz（50~70lb）、1120~2400oz（70~150lb），R29 是 >2400oz。
所以展示页把「发货重量（原表）」和「匹配区间」拆成两列，避免看串。

**为什么重量统一 oz**：计算器给出的计费重是 lb，查表时乘 16 即可；
库里混用 lb / oz 会让区间边界判断出错。

**FORMULA 档算法**：`起步价 + ceil(max(0, 计费重oz − step_from_oz) ÷ 步进oz) × step_fee_usd`。
向上取整是按整个步进单位计费，不足一个单位也算一个单位。

**当前数据**：FLAT 20 行 + FORMULA 6 行 = 26 行。
⚠️ 原表标注「适用于自 2027-02-01 起发货的商品」，但 `valid_from` 特意留空：
若现在按生效期过滤，自动带出会查不到任何数据。生效口径写在首行 `remark` 里留痕。

---

## 10. `dict_first_leg_method` 头程方式与默认单价

把「材积比」从手填参数变成由运输方式自动决定 —— 这是行业规则，不该让用户纠结。

| 字段 | 类型 | 说明 |
|---|---|---|
| `method_code` | varchar(32) | **主键**：`SEA` / `SEA_EXPRESS` / `AIR` / `EXPRESS` |
| `method_name` | varchar(64) | 海运 / 海运快线 / 空运 / 快递 |
| `default_unit_price` | decimal(10,2) | 默认头程单价 ￥/kg |
| `volume_divisor` | int unsigned | **材积比**（长×宽×高cm ÷ 此值 = 抛货重量 kg） |
| `sort_no` | int | 排序号 |
| `status` | tinyint | 1 启用 / 0 停用 |
| `remark` | varchar(256) | 备注 |

**当前数据**（参考产品表参数设置）：海运 8 元/kg、海运快线 14、空运 32、快递 55。

> 材积比的具体取值建议在入库前与物流商确认。已知：**利润计算表用 6000，产品表实测用 5000**（海运泡货）。

---

## 11. `dict_exchange_rate` 汇率（时序表）

单独建表而非放 `sys_config` 的原因：`sys_config` 的语义是"当前值"，覆盖即丢失；汇率是时序数据，需要保留历史用于画趋势曲线。

| 字段 | 类型 | 说明 |
|---|---|---|
| `currency_pair` | varchar(16) | 货币对，默认 `USD/CNY`，为多币种预留 |
| `rate` | decimal(12,6) | 汇率值。**用 decimal 不用 float** |
| `source` | varchar(16) | `API` 接口拉取 / `MANUAL` 手工录入 |
| `provider` | varchar(32) | 具体来源标识，如 `frankfurter` |
| `rate_date` | date | **业务日期**，与 currency_pair 组成唯一键 |
| `fetched_at` | datetime | 实际获取时间戳 |
| `remark` | varchar(256) | 备注 |

**唯一索引**：`(currency_pair, rate_date)`

**写入策略**：**一天一条**，同一天重复点「同步最新汇率」按钮则按唯一键 UPSERT 覆盖 —— 这样趋势是一条干净的按日曲线。若想看日内波动，改成每次 append。

**⚠️ 本表只记录历史，不提供「当前汇率」**（2026-11-10 定案）：所有业务读取当前汇率的地方
（利润测算、prefill、全局参数展示）一律走 `sys_config.DEFAULT_EXCHANGE_RATE`，不能自己
`ORDER BY rate_date DESC LIMIT 1`，否则多处口径会打架。汇率同步任务在**同一个事务**里
「按 rate_date upsert 本表」+「更新 sys_config 最新汇率」，成功后刷掉 `ProfitDictLoader` 缓存。

> ⚠️ 链路必须是「前端 → 后端 → 第三方 API」，**绝不能让前端直连**：第三方有跨域限制、API Key 会泄露、前端写不进库、无法限流。建议在国内部署时备选国内数据源。
> 数据源：主源 Frankfurter v2（`api.frankfurter.dev/v2/rate/usd/cny`，免密钥、官方参考价、返回业务日期），备源 open.er-api（`open.er-api.com/v6/latest/USD`）；两者都失败则保留上一次值，绝不让测算页报错。

---

## 12. `sys_config` 全局计算参数

兜底存放原 Excel 里散落的硬编码常量。

| 字段 | 类型 | 说明 |
|---|---|---|
| `config_key` | varchar(64) | **主键**，如 `FUEL_SURCHARGE_RATE` |
| `config_value` | varchar(256) | 配置值，统一字符串存，按 value_type 转换 |
| `value_type` | varchar(16) | `NUMBER` / `STRING` / `BOOLEAN` |
| `config_name` | varchar(128) | 中文名 |
| `remark` | varchar(256) | 说明 |
| `updated_at` | datetime | 最后更新时间 |

**建议预置项**：

| config_key | 值 | 说明 |
|---|---|---|
| `FUEL_SURCHARGE_RATE` | 0.035 | 燃油物流附加费 3.5% |
| `VOLUME_DIM_DIVISOR_INCH` | 139 | 体积重除数（长×宽×高 in ÷ 139 = lb） |
| `OZ_PER_LB` | 16 | 1 磅 = 16 盎司 |
| `REFUND_COMMISSION_KEEP` | 0.2 | 退货时佣金退还但扣留比例 20% |
| `PRICE_BAND_LOW` | 10 | 价格带分界一：< $10 |
| `PRICE_BAND_MID` | 50 | 价格带分界二：$10 ~ $50 |
| `DEFAULT_TARGET_MARGIN` | 20 | 默认目标利润率 % |
| `DEFAULT_RETURN_RATE` | 4.13 | 默认退货率 % |
| `DEFAULT_AD_RATE` | 15 | 默认广告占比 % |
| `DEFAULT_EXCHANGE_RATE` | 6.68 | **最新汇率**（中文名）：USD/CNY 当前生效值，业务读取的唯一入口；由汇率同步任务自动写入，不是人工常量。6.68 是初始基线 |

**「最新汇率」行的修改时间**（2026-11-10）：`sys_config.updated_time` 就是这一行最后被改的时间，页面上的
「修改时间 / 更新于」一律读它，不读历史表的 `fetched_at`（那是数据拉取时间）。三处入口 —— 全局参数页
「获取最新汇率」、利润测算页「取最新」、汇率变化页「同步最新汇率」—— 共用
`POST /api/exchange-rate/refresh`：后端调外部 API 拿真·最新值，一个事务里写历史表 + 更新本行，
返回 `GET /api/exchange-rate/latest` 同结构的 `ExchangeRateVO`（含 `updated_time`）。
定时任务仍按原时间自动执行，链路不变。

> ⚠️ 同步时会**显式写入 `updated_time`**，不能只靠数据库 `ON UPDATE CURRENT_TIMESTAMP`：
> 若这次拉到的汇率与上次相同，MySQL 认为行没有变化，自动时间戳不会更新，
> 用户点「获取最新汇率」就看不到任何反馈，会以为按钮坏了。

---

# 三、建议不建的表

## `dict_unit_conversion`（不建议）

`cm→inch = 0.3937008`、`kg→lb = 2.2046226`、`oz→g = 28.3495231` 这些是**数学常数**，一万年不会变。入库只会徒增一层 JOIN，建议保留在代码的常量类里。

除非你希望运营能在后台修改这些值 —— 但那显然是错误的需求，改了反而算错。

---

# 四、待确认事项

1. **`dict_first_leg_method` 的材积比取值**：5000 还是 6000？建议海运 5000（泡货）、空运 6000，但需你与物流商确认。
2. **历史数据迁移**：产品表现有那 15 项简化类目（家居与厨房等）与 46 项官方字典名字对不上，导入时这些产品的类目需**人工重新选一次**。
3. **FBA 费率版本**：`dict_fba_rate` 当前数据为 **2026-01-15 版**，需在 `valid_from` 中标注。另注意**旺季上浮逻辑**（2026-10-15 ~ 2027-01-14，×1.076）当前系统尚未实现，是否需要纳入需另行决策。
