-- =============================================================
-- 全局参数（来源：产品表「参数设置」+ dict.js CALC_CONST）
-- 由 generate_dict_sql.py 从前端 dict.js 自动生成，请勿手工编辑
-- 幂等：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO sys_config
    (config_key, config_value, value_type, config_name, remark)
VALUES
    ('DEFAULT_EXCHANGE_RATE', '6.68', 'NUMBER', '默认汇率', 'USD/CNY；前端 dict.js 默认值，实际以汇率表/接口为准'),
    ('FUEL_SURCHARGE_RATE', '0.035', 'NUMBER', '燃油与物流附加费率', '占配送费；2026-04-17 起美国站加收 3.5%'),
    ('DIM_DIVISOR_INCH', '139', 'NUMBER', '体积重除数', '立方英寸/磅；亚马逊美国站标准件'),
    ('OZ_PER_LB', '16', 'NUMBER', '磅转盎司', '数学常数，放此处便于统一配置'),
    ('REFUND_COMMISSION_KEEP', '0.2', 'NUMBER', '退货时佣金扣留比例', '退货不退的佣金占比'),
    ('PRICE_BAND_LOW', '10', 'NUMBER', '价格带下界', '售价 < $10 为 LOW 档'),
    ('PRICE_BAND_MID', '50', 'NUMBER', '价格带中界', '$10~$50 为 MID 档，> $50 为 HIGH 档'),
    ('SMALL_STANDARD_STEP_OZ', '2', 'NUMBER', '小号标准步进盎司', '每 2 oz 一档'),
    ('LARGE_STANDARD_STEP_OZ', '4', 'NUMBER', '大号标准步进盎司', '超过 48oz 后每 4 oz 步进'),
    ('LARGE_STANDARD_OZ_LIMIT', '48', 'NUMBER', '大号标准查表上限 oz', '超出后走步进费率'),
    ('DEFAULT_TARGET_MARGIN', '0.2', 'NUMBER', '默认目标利润率', '利润达标判定线'),
    ('DEFAULT_AD_RATE', '0.15', 'NUMBER', '默认广告费占比 ACoS', '占售价；新品期 15%~25%'),
    ('DEFAULT_RETURN_RATE', '0.05', 'NUMBER', '默认预估退货率', '占销量；家居厨房类 3%~5%'),
    ('DEFAULT_STORAGE_FEE_USD', '0.32', 'NUMBER', '默认入库配置费', '$/件；对应页面「合仓费」storageFeeUsd'),
    ('PEAK_SEASON_COEFFICIENT', '1.076', 'NUMBER', '旺季配送费上浮系数', '旺季窗口 2026-10-15~2027-01-14；当前系统尚未实现此逻辑')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    value_type = VALUES(value_type),
    config_name = VALUES(config_name),
    remark = VALUES(remark);
