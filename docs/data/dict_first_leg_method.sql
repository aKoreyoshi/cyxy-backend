-- =============================================================
-- 头程方式（来源：产品表「参数设置」r22~r26）
-- 由 generate_dict_sql.py 从前端 dict.js 自动生成，请勿手工编辑
-- 幂等：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO dict_first_leg_method
    (method_code, method_name, default_unit_price, volume_divisor, sort_no, status, remark, is_deleted)
VALUES
    ('SEA', '海运普船', 8.00, 5000, 1, 1, '35~45 天；轻泡货另算体积重', 0),
    ('SEA_EXPRESS', '海运快线', 14.00, 5000, 2, 1, '18~25 天；美森快船类', 0),
    ('AIR', '空运', 32.00, 6000, 3, 1, '7~12 天；补货、新品试销', 0),
    ('EXPRESS', '国际快递', 55.00, 6000, 4, 1, '3~5 天；样品、紧急补货', 0),
    ('CUSTOM', '自填', 0.00, NULL, 5, 1, '选此项后单价由用户手工填写', 0)
ON DUPLICATE KEY UPDATE
    method_name = VALUES(method_name),
    default_unit_price = VALUES(default_unit_price),
    volume_divisor = VALUES(volume_divisor),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    remark = VALUES(remark);
