-- =============================================================
-- FBA 阶梯费率（大号标准每 4oz 步进 + 大件首磅加续磅）
-- 由 generate_dict_sql.py 从前端 dict.js 自动生成，请勿手工编辑
-- 幂等：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO dict_fba_step_rate
    (tier_code, price_band, rate_type, apply_from_lb, apply_to_lb, base_usd, step_usd, step_unit, remark, valid_from, valid_to, is_deleted)
VALUES
    ('large', 'LOW', 'STEP_4OZ', 3.0000, 20.0000, 6.69, 0.0800, 'OZ4', '大号标准超出 48oz(3lb) 后按每 4oz 步进，至 20lb 止', '2026-01-15', NULL, 0),
    ('large', 'MID', 'STEP_4OZ', 3.0000, 20.0000, 7.51, 0.0800, 'OZ4', '大号标准超出 48oz(3lb) 后按每 4oz 步进，至 20lb 止', '2026-01-15', NULL, 0),
    ('large', 'HIGH', 'STEP_4OZ', 3.0000, 20.0000, 7.77, 0.0800, 'OZ4', '大号标准超出 48oz(3lb) 后按每 4oz 步进，至 20lb 止', '2026-01-15', NULL, 0),
    ('oversize', 'LOW', 'FIRST_POUND_PLUS', 0.0000, NULL, 6.78, 0.3800, 'LB', '大件不分价格带，LOW/MID/HIGH 同价，为查询统一冗余三行', '2026-01-15', NULL, 0),
    ('oversize', 'MID', 'FIRST_POUND_PLUS', 0.0000, NULL, 6.78, 0.3800, 'LB', '大件不分价格带，LOW/MID/HIGH 同价，为查询统一冗余三行', '2026-01-15', NULL, 0),
    ('oversize', 'HIGH', 'FIRST_POUND_PLUS', 0.0000, NULL, 6.78, 0.3800, 'LB', '大件不分价格带，LOW/MID/HIGH 同价，为查询统一冗余三行', '2026-01-15', NULL, 0)
ON DUPLICATE KEY UPDATE
    rate_type = VALUES(rate_type),
    apply_from_lb = VALUES(apply_from_lb),
    apply_to_lb = VALUES(apply_to_lb),
    base_usd = VALUES(base_usd),
    step_usd = VALUES(step_usd),
    step_unit = VALUES(step_unit),
    remark = VALUES(remark);
