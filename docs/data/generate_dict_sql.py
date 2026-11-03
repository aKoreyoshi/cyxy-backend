# -*- coding: utf-8 -*-
"""
字典数据 SQL 生成器

数据源：前端 src/views/tools/profit-calculator/dict.js（当前唯一真源）
用法：先跑 `node` 把 dict.js 导出成 _tmp_dict.json，再执行本脚本生成各表 SQL。

设计约定：
1. 业务编码一律用亚马逊官方类目的英文 snake_case，跨环境迁移不漂移
2. 所有 INSERT 带 ON DUPLICATE KEY UPDATE，可重复执行
3. 生效期：FBA 相关 2026-01-15（Excel 原表标注）；佣金未标注故留 NULL
"""
import json

SRC = "_tmp_dict.json"
OUT = "."

j = json.load(open(SRC, encoding="utf-8"))

# ============================================================
# 46 项佣金类目的业务编码（手工映射，与退货阈值字典保持同名同码）
# ============================================================
COMMISSION_CODE = {
    "亚马逊设备配件": "amazon_device_accessories",
    "Amazon Explore": "amazon_explore",
    "母婴(婴儿服装除外)": "baby_except_apparel",
    "图书（包括收藏类图书）": "books",
    "摄影摄像": "camera_photo",
    "手机设备": "cell_phone_devices",
    "电视/音响": "television_audio",
    "电子产品配件": "electronics_accessories",
    "家具(包括户外家具)": "furniture",
    "家居与园艺": "home_garden",
    "厨房用品": "kitchen",
    "全尺寸电器": "full_size_appliances",
    "小型电器(包括零件和配件)": "small_appliances",
    "床垫": "mattresses",
    "音乐": "music",
    "乐器": "musical_instruments",
    "办公用品": "office_products",
    "户外用品": "outdoors",
    "个人电脑": "personal_computers",
    "宠物用品": "pet_supplies",
    "软件和电脑/视频游戏": "software_computer_video_games",
    "体育用品（体育收藏品除外）": "sporting_goods",
    "工具和家居装修(不含电)": "tools_home_improvement",
    "工具和家居装修(电动工具)": "tools_home_improvement_power_tools",
    "玩具和游戏": "toys_games",
    "视频和 DVD": "video_dvd",
    "视频游戏机": "video_game_consoles",
    "其他": "everything_else",
    "3D 打印商品": "printed_3d_products",
    "汽车用品(非轮胎和轮毂)": "automotive_excl_tires_wheels",
    "汽车用品(轮胎和轮毂)": "automotive_tires_wheels",
    "美妆": "beauty",
    "服装与配饰（包括运动装）": "apparel_accessories",
    "收藏类图书": "collectible_books",
    "硬币收藏品": "collectible_coins",
    "娱乐收藏品": "entertainment_collectibles",
    "艺术品": "fine_art",
    "礼品卡": "gift_cards",
    "食品": "grocery",
    "个护健康(含个护用品)": "health_personal_care",
    "工业和科学用品(含餐饮服务和清洁与卫生)": "industrial_scientific",
    "珠宝首饰": "jewelry",
    "箱包和旅行用品": "luggage_travel",
    "鞋靴、手提包和太阳镜": "shoes_handbags_sunglasses",
    "体育收藏品": "sports_collectibles",
    "钟表": "watches",
}


def esc(s):
    return "NULL" if s is None else "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def num(v, nd=2):
    return "NULL" if v is None else f"{float(v):.{nd}f}"


def write(fname, header, table, cols, rows, updcols):
    body = ",\n".join("    (" + ", ".join(r) + ")" for r in rows)
    sql = f"""-- =============================================================
-- {header}
-- 由 generate_dict_sql.py 从前端 dict.js 自动生成，请勿手工编辑
-- 幂等：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO {table}
    ({", ".join(cols)})
VALUES
{body}
ON DUPLICATE KEY UPDATE
""" + ",\n".join(f"    {c} = VALUES({c})" for c in updcols) + ";\n"
    open(f"{OUT}/{fname}", "w", encoding="utf-8").write(sql)
    print(f"  生成 {fname:<42} {len(rows):>3} 行")


# ------------------------------------------------------------
# 1. dict_category_commission（46 项）
# ------------------------------------------------------------
rows = []
for i, c in enumerate(j["commissionCategories"], 1):
    name = c["name"]
    code = COMMISSION_CODE[name]
    mode = c["mode"].upper()          # FLAT / FULL / MARGINAL
    flat_rate = c.get("rate") if c["mode"] == "flat" else None
    rows.append([
        esc(code), esc(name), esc(mode), num(flat_rate, 4),
        num(c.get("minCommission")), "1" if c.get("needApproval") else "0",
        esc(c.get("remark")), str(i), "1", "NULL", "NULL", "0",
    ])
write("dict_category_commission.sql", "销售佣金类目字典（46 项，三种费率模式）",
      "dict_category_commission",
      ["category_code", "category_name", "fee_mode", "flat_rate", "min_commission_usd",
       "need_approval", "remark", "sort_no", "status", "valid_from", "valid_to", "is_deleted"],
      rows,
      ["category_name", "fee_mode", "flat_rate", "min_commission_usd",
       "need_approval", "remark", "sort_no", "status"])

# ------------------------------------------------------------
# 2. dict_category_commission_tier（FULL / MARGINAL 的阶梯档位）
# ------------------------------------------------------------
rows = []
for c in j["commissionCategories"]:
    if c["mode"] not in ("full", "marginal"):
        continue
    code = COMMISSION_CODE[c["name"]]
    prev_max = 0.0
    for tno, t in enumerate(c["tiers"], 1):
        rows.append([
            esc(code), str(tno), num(prev_max), num(t["max"]),
            num(t["rate"], 4), "NULL",
            esc(f"{c['mode']} 模式第 {tno} 档；最低佣金按整单计，见父级 min_commission_usd"),
            str(tno),
        ])
        if t["max"] is not None:
            prev_max = float(t["max"])
write("dict_category_commission_tier.sql", "阶梯佣金档位（FULL 跳档 4 项 + MARGINAL 累进 9 项）",
      "dict_category_commission_tier",
      ["category_code", "tier_no", "price_min_usd", "price_max_usd", "tier_rate",
       "min_commission_usd", "remark", "sort_no"],
      rows,
      ["price_min_usd", "price_max_usd", "tier_rate", "min_commission_usd", "remark", "sort_no"])

# ------------------------------------------------------------
# 3. dict_fba_size_tier（3 档）
# ------------------------------------------------------------
VR = "2026-01-15"
st = j["sizeTierRules"]
rows = [
    ["'small'", esc(st["smallStandard"]["label"]), "1",
     num(st["smallStandard"]["maxLength"], 4), num(st["smallStandard"]["maxWidth"], 4),
     num(st["smallStandard"]["maxHeight"], 4), num(st["smallStandard"]["maxWeightLb"], 4),
     "'ACTUAL'", esc("查费率时用实重；超过任一上限即落入下一档"), esc(VR), "NULL", "0"],
    ["'large'", esc(st["largeStandard"]["label"]), "2",
     num(st["largeStandard"]["maxLength"], 4), num(st["largeStandard"]["maxWidth"], 4),
     num(st["largeStandard"]["maxHeight"], 4), num(st["largeStandard"]["maxWeightLb"], 4),
     "'BILLABLE'", esc("查费率时用计费重（实重与体积重取大）"), esc(VR), "NULL", "0"],
    ["'oversize'", esc("大件超大件"), "3",
     "NULL", "NULL", "NULL", "NULL",
     "'BILLABLE'", esc("兜底档：超出大号标准即为此档，不限尺寸重量"), esc(VR), "NULL", "0"],
]
write("dict_fba_size_tier.sql", "FBA 尺寸分段（小号标准 / 大号标准 / 大件超大件）",
      "dict_fba_size_tier",
      ["tier_code", "tier_name", "sort_no", "max_length_inch", "max_width_inch",
       "max_height_inch", "max_weight_lb", "weight_basis", "remark",
       "valid_from", "valid_to", "is_deleted"],
      rows,
      ["tier_name", "sort_no", "max_length_inch", "max_width_inch", "max_height_inch",
       "max_weight_lb", "weight_basis", "remark"])

# ------------------------------------------------------------
# 4. dict_fba_rate（费率矩阵行式化：8×3 + 12×3 = 60 行）
# ------------------------------------------------------------
BANDS = [("low", "LOW"), ("mid", "MID"), ("high", "HIGH")]
rows = []
for tier_code, arr in [("small", j["fbaSmallStandardRates"]), ("large", j["fbaLargeStandardRates"])]:
    prev = 0.0
    for r in arr:
        for src, band in BANDS:
            rows.append([
                esc(tier_code), esc(band), num(prev, 4), num(r["ozCap"], 4),
                num(r[src]), esc(VR), "NULL", "0",
            ])
        prev = float(r["ozCap"])
write("dict_fba_rate.sql", "FBA 配送费矩阵（小号 8 档 + 大号 12 档，各 3 个价格带 = 60 行）",
      "dict_fba_rate",
      ["tier_code", "price_band", "weight_min_oz", "weight_max_oz", "rate_usd",
       "valid_from", "valid_to", "is_deleted"],
      rows,
      ["weight_min_oz", "weight_max_oz", "rate_usd", "valid_from"])

# ------------------------------------------------------------
# 5. dict_fba_step_rate（大号标准 3~20lb 步进 + 大件首磅加续磅）
# ------------------------------------------------------------
rows = []
CC = j["CALC_CONST"]
for src, band in BANDS:
    s = j["fbaStepRates"][src]
    rows.append([
        "'large'", esc(band), "'STEP_4OZ'", "3.0000", "20.0000",
        num(s["base"]), num(s["step"], 4), "'OZ4'",
        esc(f"大号标准超出 {int(CC['largeStandardOzLimit'])}oz(3lb) 后按每 4oz 步进，至 20lb 止"),
        esc(VR), "NULL", "0",
    ])
ov = j["fbaOversizeRates"]
for src, band in BANDS:
    rows.append([
        "'oversize'", esc(band), "'FIRST_POUND_PLUS'", "0.0000", "NULL",
        num(ov["firstPound"]), num(ov["perExtraPound"], 4), "'LB'",
        esc("大件不分价格带，LOW/MID/HIGH 同价，为查询统一冗余三行"), esc(VR), "NULL", "0",
    ])
write("dict_fba_step_rate.sql", "FBA 阶梯费率（大号标准每 4oz 步进 + 大件首磅加续磅）",
      "dict_fba_step_rate",
      ["tier_code", "price_band", "rate_type", "apply_from_lb", "apply_to_lb",
       "base_usd", "step_usd", "step_unit", "remark", "valid_from", "valid_to", "is_deleted"],
      rows,
      ["rate_type", "apply_from_lb", "apply_to_lb", "base_usd", "step_usd", "step_unit", "remark"])

# ------------------------------------------------------------
# 6. dict_first_leg_method（来自产品表「参数设置」头程单价表）
# ------------------------------------------------------------
# ⚠️ volume_divisor 为建议值（海运泡货 5000 / 空运 6000），需与物流商确认
rows = [
    ["'SEA'", esc("海运普船"), "8.00", "5000", "1", "1", esc("35~45 天；轻泡货另算体积重"), "0"],
    ["'SEA_EXPRESS'", esc("海运快线"), "14.00", "5000", "2", "1", esc("18~25 天；美森快船类"), "0"],
    ["'AIR'", esc("空运"), "32.00", "6000", "3", "1", esc("7~12 天；补货、新品试销"), "0"],
    ["'EXPRESS'", esc("国际快递"), "55.00", "6000", "4", "1", esc("3~5 天；样品、紧急补货"), "0"],
    ["'CUSTOM'", esc("自填"), "0.00", "NULL", "5", "1", esc("选此项后单价由用户手工填写"), "0"],
]
write("dict_first_leg_method.sql", "头程方式（来源：产品表「参数设置」r22~r26）",
      "dict_first_leg_method",
      ["method_code", "method_name", "default_unit_price", "volume_divisor",
       "sort_no", "status", "remark", "is_deleted"],
      rows,
      ["method_name", "default_unit_price", "volume_divisor", "sort_no", "status", "remark"])

# ------------------------------------------------------------
# 7. sys_config（全局参数）
# ------------------------------------------------------------
rows = [
    ["'DEFAULT_EXCHANGE_RATE'", esc("6.68"), "'NUMBER'", esc("默认汇率"), esc("USD/CNY；前端 dict.js 默认值，实际以汇率表/接口为准")],
    ["'FUEL_SURCHARGE_RATE'", esc("0.035"), "'NUMBER'", esc("燃油与物流附加费率"), esc("占配送费；2026-04-17 起美国站加收 3.5%")],
    ["'DIM_DIVISOR_INCH'", esc("139"), "'NUMBER'", esc("体积重除数"), esc("立方英寸/磅；亚马逊美国站标准件")],
    ["'OZ_PER_LB'", esc("16"), "'NUMBER'", esc("磅转盎司"), esc("数学常数，放此处便于统一配置")],
    ["'REFUND_COMMISSION_KEEP'", esc("0.2"), "'NUMBER'", esc("退货时佣金扣留比例"), esc("退货不退的佣金占比")],
    ["'PRICE_BAND_LOW'", esc("10"), "'NUMBER'", esc("价格带下界"), esc("售价 < $10 为 LOW 档")],
    ["'PRICE_BAND_MID'", esc("50"), "'NUMBER'", esc("价格带中界"), esc("$10~$50 为 MID 档，> $50 为 HIGH 档")],
    ["'SMALL_STANDARD_STEP_OZ'", esc("2"), "'NUMBER'", esc("小号标准步进盎司"), esc("每 2 oz 一档")],
    ["'LARGE_STANDARD_STEP_OZ'", esc("4"), "'NUMBER'", esc("大号标准步进盎司"), esc("超过 48oz 后每 4 oz 步进")],
    ["'LARGE_STANDARD_OZ_LIMIT'", esc("48"), "'NUMBER'", esc("大号标准查表上限 oz"), esc("超出后走步进费率")],
    ["'DEFAULT_TARGET_MARGIN'", esc("0.2"), "'NUMBER'", esc("默认目标利润率"), esc("利润达标判定线")],
    ["'DEFAULT_AD_RATE'", esc("0.15"), "'NUMBER'", esc("默认广告费占比 ACoS"), esc("占售价；新品期 15%~25%")],
    ["'DEFAULT_RETURN_RATE'", esc("0.05"), "'NUMBER'", esc("默认预估退货率"), esc("占销量；家居厨房类 3%~5%")],
    ["'DEFAULT_STORAGE_FEE_USD'", esc("0.32"), "'NUMBER'", esc("默认入库配置费"), esc("$/件；对应页面「合仓费」storageFeeUsd")],
    ["'PEAK_SEASON_COEFFICIENT'", esc("1.076"), "'NUMBER'", esc("旺季配送费上浮系数"), esc("旺季窗口 2026-10-15~2027-01-14；当前系统尚未实现此逻辑")],
]
write("sys_config.sql", "全局参数（来源：产品表「参数设置」+ dict.js CALC_CONST）",
      "sys_config",
      ["config_key", "config_value", "value_type", "config_name", "remark"],
      rows,
      ["config_value", "value_type", "config_name", "remark"])

# ------------------------------------------------------------
# 8. dict_exchange_rate（基线一条，来自 Excel 汇率附件）
# ------------------------------------------------------------
rows = [[
    "'USD/CNY'", num(j["DEFAULT_EXCHANGE_RATE"], 6), "'MANUAL'", esc("excel_attachment"),
    esc("2026-10-30"), esc("2026-10-30 15:43:12"),
    esc("初始化基线：源自 Excel《汇率附件》(6.68)。接入实时汇率接口后以 API 取值为准"),
]]
write("dict_exchange_rate.sql", "汇率基线（源自 Excel 汇率附件，更新时间 2026-10-30 15:43:12）",
      "dict_exchange_rate",
      ["currency_pair", "rate", "source", "provider", "rate_date", "fetched_at", "remark"],
      rows,
      ["rate", "source", "provider", "fetched_at", "remark"])

print("\n全部生成完毕")
