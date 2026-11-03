-- =============================================================
-- 阶梯佣金档位（FULL 跳档 4 项 + MARGINAL 累进 9 项）
-- 由 generate_dict_sql.py 从前端 dict.js 自动生成，请勿手工编辑
-- 幂等：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO dict_category_commission_tier
    (category_code, tier_no, price_min_usd, price_max_usd, tier_rate, min_commission_usd, remark, sort_no)
VALUES
    ('baby_except_apparel', 1, 0.00, 10.00, 0.0800, NULL, 'full 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('baby_except_apparel', 2, 10.00, NULL, 0.1500, NULL, 'full 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('electronics_accessories', 1, 0.00, 100.00, 0.1500, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('electronics_accessories', 2, 100.00, NULL, 0.0800, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('furniture', 1, 0.00, 200.00, 0.1500, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('furniture', 2, 200.00, NULL, 0.1000, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('small_appliances', 1, 0.00, 300.00, 0.1500, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('small_appliances', 2, 300.00, NULL, 0.0800, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('beauty', 1, 0.00, 10.00, 0.0800, NULL, 'full 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('beauty', 2, 10.00, NULL, 0.1500, NULL, 'full 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('collectible_coins', 1, 0.00, 250.00, 0.1500, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('collectible_coins', 2, 250.00, 1000.00, 0.1000, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('collectible_coins', 3, 1000.00, NULL, 0.0600, NULL, 'marginal 模式第 3 档；最低佣金按整单计，见父级 min_commission_usd', 3),
    ('entertainment_collectibles', 1, 0.00, 100.00, 0.2000, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('entertainment_collectibles', 2, 100.00, 1000.00, 0.1000, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('entertainment_collectibles', 3, 1000.00, NULL, 0.0600, NULL, 'marginal 模式第 3 档；最低佣金按整单计，见父级 min_commission_usd', 3),
    ('fine_art', 1, 0.00, 100.00, 0.2000, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('fine_art', 2, 100.00, 1000.00, 0.1500, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('fine_art', 3, 1000.00, 5000.00, 0.1000, NULL, 'marginal 模式第 3 档；最低佣金按整单计，见父级 min_commission_usd', 3),
    ('fine_art', 4, 5000.00, NULL, 0.0500, NULL, 'marginal 模式第 4 档；最低佣金按整单计，见父级 min_commission_usd', 4),
    ('grocery', 1, 0.00, 15.00, 0.0800, NULL, 'full 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('grocery', 2, 15.00, NULL, 0.1500, NULL, 'full 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('health_personal_care', 1, 0.00, 10.00, 0.0800, NULL, 'full 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('health_personal_care', 2, 10.00, NULL, 0.1500, NULL, 'full 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('jewelry', 1, 0.00, 250.00, 0.2000, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('jewelry', 2, 250.00, NULL, 0.0500, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('sports_collectibles', 1, 0.00, 100.00, 0.1500, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('sports_collectibles', 2, 100.00, 1000.00, 0.1000, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2),
    ('sports_collectibles', 3, 1000.00, NULL, 0.0600, NULL, 'marginal 模式第 3 档；最低佣金按整单计，见父级 min_commission_usd', 3),
    ('watches', 1, 0.00, 1500.00, 0.1600, NULL, 'marginal 模式第 1 档；最低佣金按整单计，见父级 min_commission_usd', 1),
    ('watches', 2, 1500.00, NULL, 0.0300, NULL, 'marginal 模式第 2 档；最低佣金按整单计，见父级 min_commission_usd', 2)
ON DUPLICATE KEY UPDATE
    price_min_usd = VALUES(price_min_usd),
    price_max_usd = VALUES(price_max_usd),
    tier_rate = VALUES(tier_rate),
    min_commission_usd = VALUES(min_commission_usd),
    remark = VALUES(remark),
    sort_no = VALUES(sort_no);
