-- =============================================================
-- FBA 尺寸分段（小号标准 / 大号标准 / 大件超大件）
-- 由 generate_dict_sql.py 从前端 dict.js 自动生成，请勿手工编辑
-- 幂等：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO dict_fba_size_tier
    (tier_code, tier_name, sort_no, max_length_inch, max_width_inch, max_height_inch, max_weight_lb, weight_basis, remark, valid_from, valid_to, is_deleted)
VALUES
    ('small', '小号标准尺寸', 1, 15.0000, 12.0000, 0.7500, 1.0000, 'ACTUAL', '查费率时用实重；超过任一上限即落入下一档', '2026-01-15', NULL, 0),
    ('large', '大号标准尺寸', 2, 18.0000, 14.0000, 8.0000, 20.0000, 'BILLABLE', '查费率时用计费重（实重与体积重取大）', '2026-01-15', NULL, 0),
    ('oversize', '大件超大件', 3, NULL, NULL, NULL, NULL, 'BILLABLE', '兜底档：超出大号标准即为此档，不限尺寸重量', '2026-01-15', NULL, 0)
ON DUPLICATE KEY UPDATE
    tier_name = VALUES(tier_name),
    sort_no = VALUES(sort_no),
    max_length_inch = VALUES(max_length_inch),
    max_width_inch = VALUES(max_width_inch),
    max_height_inch = VALUES(max_height_inch),
    max_weight_lb = VALUES(max_weight_lb),
    weight_basis = VALUES(weight_basis),
    remark = VALUES(remark);
