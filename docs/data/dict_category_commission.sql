-- =============================================================
-- 销售佣金类目字典（46 项，三种费率模式）
-- 由 generate_dict_sql.py 从前端 dict.js 自动生成，请勿手工编辑
-- 幂等：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO dict_category_commission
    (category_code, category_name, fee_mode, flat_rate, min_commission_usd, need_approval, remark, sort_no, status, valid_from, valid_to, is_deleted)
VALUES
    ('amazon_device_accessories', '亚马逊设备配件', 'FLAT', 0.4500, 0.30, 0, '收取45%的销售佣金', 1, 1, NULL, NULL, 0),
    ('amazon_explore', 'Amazon Explore', 'FLAT', 0.3000, 2.00, 0, '收取30%（虚拟体验）的销售佣金', 2, 1, NULL, NULL, 0),
    ('baby_except_apparel', '母婴(婴儿服装除外)', 'FULL', NULL, 0.30, 0, '总价 ≤ $10 收 8%，超出后整套按 15%', 3, 1, NULL, NULL, 0),
    ('books', '图书（包括收藏类图书）', 'FLAT', 0.1500, NULL, 0, '收取15%的销售佣金', 4, 1, NULL, NULL, 0),
    ('camera_photo', '摄影摄像', 'FLAT', 0.0800, 0.30, 0, '收取8%的销售佣金', 5, 1, NULL, NULL, 0),
    ('cell_phone_devices', '手机设备', 'FLAT', 0.0800, 0.30, 0, '收取8%的销售佣金', 6, 1, NULL, NULL, 0),
    ('television_audio', '电视/音响', 'FLAT', 0.0800, 0.30, 0, '收取8%的销售佣金', 7, 1, NULL, NULL, 0),
    ('electronics_accessories', '电子产品配件', 'MARGINAL', NULL, 0.30, 0, '$100 以内部分 15%，超出部分 8%', 8, 1, NULL, NULL, 0),
    ('furniture', '家具(包括户外家具)', 'MARGINAL', NULL, 0.30, 0, '$200 以内部分 15%，超出部分 10%', 9, 1, NULL, NULL, 0),
    ('home_garden', '家居与园艺', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 10, 1, NULL, NULL, 0),
    ('kitchen', '厨房用品', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 11, 1, NULL, NULL, 0),
    ('full_size_appliances', '全尺寸电器', 'FLAT', 0.0800, 0.30, 0, '收取8%的销售佣金', 12, 1, NULL, NULL, 0),
    ('small_appliances', '小型电器(包括零件和配件)', 'MARGINAL', NULL, 0.30, 0, '$300 以内部分 15%，超出部分 8%', 13, 1, NULL, NULL, 0),
    ('mattresses', '床垫', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 14, 1, NULL, NULL, 0),
    ('music', '音乐', 'FLAT', 0.1500, NULL, 0, '收取15%的销售佣金', 15, 1, NULL, NULL, 0),
    ('musical_instruments', '乐器', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 16, 1, NULL, NULL, 0),
    ('office_products', '办公用品', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 17, 1, NULL, NULL, 0),
    ('outdoors', '户外用品', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 18, 1, NULL, NULL, 0),
    ('personal_computers', '个人电脑', 'FLAT', 0.0600, 0.30, 0, '收取6%的销售佣金', 19, 1, NULL, NULL, 0),
    ('pet_supplies', '宠物用品', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 20, 1, NULL, NULL, 0),
    ('software_computer_video_games', '软件和电脑/视频游戏', 'FLAT', 0.1500, NULL, 0, '收取15%的销售佣金', 21, 1, NULL, NULL, 0),
    ('sporting_goods', '体育用品（体育收藏品除外）', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 22, 1, NULL, NULL, 0),
    ('tools_home_improvement', '工具和家居装修(不含电)', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 23, 1, NULL, NULL, 0),
    ('tools_home_improvement_power_tools', '工具和家居装修(电动工具)', 'FLAT', 0.1200, NULL, 0, '基础设备电动工具为12%', 24, 1, NULL, NULL, 0),
    ('toys_games', '玩具和游戏', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 25, 1, NULL, NULL, 0),
    ('video_dvd', '视频和 DVD', 'FLAT', 0.1500, NULL, 0, '收取15%的销售佣金', 26, 1, NULL, NULL, 0),
    ('video_game_consoles', '视频游戏机', 'FLAT', 0.0800, 0.30, 0, '收取8%的销售佣金', 27, 1, NULL, NULL, 0),
    ('everything_else', '其他', 'FLAT', 0.1500, 0.30, 0, '收取15%的销售佣金', 28, 1, NULL, NULL, 0),
    ('printed_3d_products', '3D 打印商品', 'FLAT', 0.1200, 0.30, 1, '收取12%的销售佣金', 29, 1, NULL, NULL, 0),
    ('automotive_excl_tires_wheels', '汽车用品(非轮胎和轮毂)', 'FLAT', 0.1200, 0.30, 1, '收取12%的销售佣金', 30, 1, NULL, NULL, 0),
    ('automotive_tires_wheels', '汽车用品(轮胎和轮毂)', 'FLAT', 0.1000, NULL, 1, '轮胎和轮毂商品为10%', 31, 1, NULL, NULL, 0),
    ('beauty', '美妆', 'FULL', NULL, 0.30, 1, '总价 ≤ $10 收 8%，超出后整套按 15%', 32, 1, NULL, NULL, 0),
    ('apparel_accessories', '服装与配饰（包括运动装）', 'FLAT', 0.1700, 0.30, 1, '收取17%的销售佣金', 33, 1, NULL, NULL, 0),
    ('collectible_books', '收藏类图书', 'FLAT', 0.1500, NULL, 1, '收取15%的销售佣金', 34, 1, NULL, NULL, 0),
    ('collectible_coins', '硬币收藏品', 'MARGINAL', NULL, 0.30, 1, '$250 以内 15%、$250~1000 10%、超出 6%', 35, 1, NULL, NULL, 0),
    ('entertainment_collectibles', '娱乐收藏品', 'MARGINAL', NULL, NULL, 1, '$100 以内 20%、$100~1000 10%、超出 6%', 36, 1, NULL, NULL, 0),
    ('fine_art', '艺术品', 'MARGINAL', NULL, NULL, 1, '100/1000/5000 三档递进：20%、15%、10%、5%', 37, 1, NULL, NULL, 0),
    ('gift_cards', '礼品卡', 'FLAT', 0.2000, NULL, 1, '收取20%的销售佣金', 38, 1, NULL, NULL, 0),
    ('grocery', '食品', 'FULL', NULL, NULL, 1, '总价 ≤ $15 收 8%，超出后整套按 15%', 39, 1, NULL, NULL, 0),
    ('health_personal_care', '个护健康(含个护用品)', 'FULL', NULL, 0.30, 1, '总价 ≤ $10 收 8%，超出后整套按 15%', 40, 1, NULL, NULL, 0),
    ('industrial_scientific', '工业和科学用品(含餐饮服务和清洁与卫生)', 'FLAT', 0.1200, 0.30, 1, '收取12%的销售佣金', 41, 1, NULL, NULL, 0),
    ('jewelry', '珠宝首饰', 'MARGINAL', NULL, 0.30, 1, '$250 以内 20%、超出部分 5%', 42, 1, NULL, NULL, 0),
    ('luggage_travel', '箱包和旅行用品', 'FLAT', 0.1500, 0.30, 1, '收取15%的销售佣金', 43, 1, NULL, NULL, 0),
    ('shoes_handbags_sunglasses', '鞋靴、手提包和太阳镜', 'FLAT', 0.1500, 0.30, 1, '收取15%的销售佣金', 44, 1, NULL, NULL, 0),
    ('sports_collectibles', '体育收藏品', 'MARGINAL', NULL, NULL, 1, '$100 以内 15%、$100~1000 10%、超出 6%', 45, 1, NULL, NULL, 0),
    ('watches', '钟表', 'MARGINAL', NULL, 0.30, 1, '$1500 以内 16%、超出部分 3%', 46, 1, NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    category_name = VALUES(category_name),
    fee_mode = VALUES(fee_mode),
    flat_rate = VALUES(flat_rate),
    min_commission_usd = VALUES(min_commission_usd),
    need_approval = VALUES(need_approval),
    remark = VALUES(remark),
    sort_no = VALUES(sort_no),
    status = VALUES(status);
