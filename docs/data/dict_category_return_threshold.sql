-- =============================================================
-- 类目退货阈值字典初始化数据（33 项）
-- 数据来源：《cyxy-利润计算表格 - 副本.xlsm》→ sheet《类目退货阈值附件》
--           （原表标注「2024 年 6 月 1 日生效」，故 valid_from = 2024-06-01）
--           与前端 src/views/tools/profit-calculator/dict.js 的 returnThresholds 逐项核对一致
-- 编码规则：亚马逊官方类目英文名的 snake_case，主键业务编码，跨环境迁移不漂移
-- 幂等说明：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO dict_category_return_threshold
    (threshold_code, category_name, threshold_rate, remark, sort_no, status, valid_from, valid_to, is_deleted)
VALUES
    ('amazon_device_accessories',  '亚马逊设备配件',   0.113000, '2024-06-01 生效版', 1,  1, '2024-06-01', NULL, 0),
    ('automotive',                 '汽车用品',         0.091000, '2024-06-01 生效版', 2,  1, '2024-06-01', NULL, 0),
    ('baby',                       '母婴',             0.093000, '2024-06-01 生效版', 3,  1, '2024-06-01', NULL, 0),
    ('backpacks_handbags_luggage', '背包、手提包和箱包', 0.128000, '2024-06-01 生效版', 4,  1, '2024-06-01', NULL, 0),
    ('base_equipment_power_tools', '基础设备电动工具',   0.071000, '2024-06-01 生效版', 5,  1, '2024-06-01', NULL, 0),
    ('beauty_personal_care',       '美妆和个护健康',     0.055000, '2024-06-01 生效版', 6,  1, '2024-06-01', NULL, 0),
    ('business_industrial_scientific', '商业、工业与科学用品', 0.060000, '2024-06-01 生效版', 7, 1, '2024-06-01', NULL, 0),
    ('small_appliances',           '小型电器',         0.126000, '2024-06-01 生效版', 8,  1, '2024-06-01', NULL, 0),
    ('computers',                  '电脑',             0.114000, '2024-06-01 生效版', 9,  1, '2024-06-01', NULL, 0),
    ('consumer_electronics',       '消费类电子产品',     0.112000, '2024-06-01 生效版', 10, 1, '2024-06-01', NULL, 0),
    ('electronics_accessories',    '电子产品配件',       0.088000, '2024-06-01 生效版；Excel 原表此项带脚注①', 11, 1, '2024-06-01', NULL, 0),
    ('everything_else',            '所有其他分类',       0.048000, '2024-06-01 生效版；Excel 原表此项带脚注②', 12, 1, '2024-06-01', NULL, 0),
    ('eyewear',                    '眼镜',             0.121000, '2024-06-01 生效版', 13, 1, '2024-06-01', NULL, 0),
    ('fine_art',                   '艺术品',           0.056000, '2024-06-01 生效版', 14, 1, '2024-06-01', NULL, 0),
    ('full_size_appliances',       '全尺寸电器',         0.119000, '2024-06-01 生效版', 15, 1, '2024-06-01', NULL, 0),
    ('furniture',                  '家具',             0.096000, '2024-06-01 生效版', 16, 1, '2024-06-01', NULL, 0),
    ('grocery',                    '食品',             0.029000, '2024-06-01 生效版', 17, 1, '2024-06-01', NULL, 0),
    ('home_kitchen',               '家居及厨房用品',     0.081000, '2024-06-01 生效版', 18, 1, '2024-06-01', NULL, 0),
    ('jewelry',                    '珠宝首饰',           0.108000, '2024-06-01 生效版', 19, 1, '2024-06-01', NULL, 0),
    ('lawn_garden',                '草坪和花园',         0.077000, '2024-06-01 生效版', 20, 1, '2024-06-01', NULL, 0),
    ('lawn_mowers_snow_blowers',   '割草机和除雪机',     0.095000, '2024-06-01 生效版', 21, 1, '2024-06-01', NULL, 0),
    ('mattresses',                 '床垫',             0.093000, '2024-06-01 生效版', 22, 1, '2024-06-01', NULL, 0),
    ('media_books_dvd_music_software_video', '媒介类商品 - 图书、DVD、音乐、软件、视频', 0.051000, '2024-06-01 生效版', 23, 1, '2024-06-01', NULL, 0),
    ('musical_instruments_av_production', '乐器和影音制作', 0.092000, '2024-06-01 生效版', 24, 1, '2024-06-01', NULL, 0),
    ('office_products',            '办公用品',           0.044000, '2024-06-01 生效版', 25, 1, '2024-06-01', NULL, 0),
    ('pet_supplies',               '宠物用品',           0.102000, '2024-06-01 生效版', 26, 1, '2024-06-01', NULL, 0),
    ('sporting_goods',             '运动户外',           0.087000, '2024-06-01 生效版', 27, 1, '2024-06-01', NULL, 0),
    ('tires',                      '轮胎',             0.087000, '2024-06-01 生效版', 28, 1, '2024-06-01', NULL, 0),
    ('tools_home_improvement',     '工具和家居装修',     0.087000, '2024-06-01 生效版', 29, 1, '2024-06-01', NULL, 0),
    ('toys_games',                 '玩具和游戏',         0.047000, '2024-06-01 生效版', 30, 1, '2024-06-01', NULL, 0),
    ('video_game_consoles',        '视频游戏机',         0.107000, '2024-06-01 生效版', 31, 1, '2024-06-01', NULL, 0),
    ('video_games_accessories',    '视频游戏和游戏配件',  0.065000, '2024-06-01 生效版', 32, 1, '2024-06-01', NULL, 0),
    ('watches',                    '钟表',             0.120000, '2024-06-01 生效版', 33, 1, '2024-06-01', NULL, 0)
ON DUPLICATE KEY UPDATE
    category_name   = VALUES(category_name),
    threshold_rate  = VALUES(threshold_rate),
    remark          = VALUES(remark),
    sort_no         = VALUES(sort_no),
    status          = VALUES(status),
    valid_from      = VALUES(valid_from),
    valid_to        = VALUES(valid_to),
    is_deleted      = 0;
