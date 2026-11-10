-- =====================================================================
-- 2026-11-04 权限控制一期：初始化数据（依赖 2026-11-04_rbac_tables.sql）
-- 2026-11-05 更新：菜单顺序 / 命名 / id 重新整理
-- ---------------------------------------------------------------------
-- 内容：内置超管角色 + macong 超管账号 + 菜单树 + 写接口清单 + 超管绑角色。
--
-- 设计要点：
--   1) 只插 1 个超管账号。另外 2 个账号请上线后在「用户管理」页自己建、
--      自己分角色，正好把新功能从头到尾自测一遍。
--   2) 超管角色【不写】 sys_role_menu —— 拦截器对 is_builtin=1 直接全放行，
--      菜单树也走「全量」分支。这样权限表被清空也不会把管理员锁在外面。
--   3) 菜单 id 从 1 连续编到 21：一级菜单 1~5（首页 / 系统管理 / 产品管理 /
--      运营工具 / 参数列表），子页 6~21，与 router/menu.json 的清单逐条对应。
--      id=21「尾程退货费」于 2026-11-10 追加（历史库请改跑
--      docs/data/2026-11-10_menu_add_return_fee.sql，它还负责给普通角色补授权）。
--      ⚠️ 本脚本只适用于【全新空库】。sys_menu 只有主键 id、route_name 没有
--         唯一索引，历史库直接重跑会按新 id 再插一份重复菜单。已有菜单数据的
--         库请改跑 docs/data/2026-11-05_menu_reorder.sql（按 route_name 幂等重排）。
--   4) menu_type 沿用前端 V3S 语义：0 显示 / 1 隐藏功能页。
--      模板示例菜单（菜单示例 / 组件示例 / 页面示例）一概不导入。
--   5) write_api_patterns 只挂在「角色会实际勾选的叶子菜单」上。
--      不能挂在父目录：菜单树构建时会自动补祖先目录，但 sys_role_menu
--      表里并没有那一行，拦截器查 rm 会得到 null，反而把正常授权误判成越权。
--      明确不拦 /api/profit/calc（POST 但只算不写库）。
--   6) sort_no 决定侧边栏顺序。首页必须是一级菜单里最小的：
--      router/index.js 会跳「第一个有 url 的菜单」，挪走首页会跳错默认页。
--   7) 系统管理三个前端页面已于 2026-11-05 补齐，这里默认 visible=1 启用。
--      不用担心越权：RbacQueryService 对非超管角色一律过滤 SYSTEM_ 前缀菜单。
--      历史库若曾跑过 visible=0 的旧版本，请另执行 2026-11-05_system_menu_enable.sql
--      （本脚本的 ON DUPLICATE KEY UPDATE 不含 visible，重跑不会改已有行）。
--
-- 幂等：账号 / 角色 / 关联用 INSERT IGNORE（不覆盖已改过的密码）；
--       菜单用 ON DUPLICATE KEY UPDATE，只更新「技术字段」，
--       不覆盖管理员在页面上改过的标题、图标、排序和启停状态。
--
-- 部署顺序：全新环境 = rbac_tables.sql → 本脚本（一步到位，不需要另两个脚本）
--           历史环境 = 先 2026-11-05_menu_reorder.sql 重排，再按需跑
--                      2026-11-05_system_menu_enable.sql 启用系统管理菜单
-- =====================================================================

SET NAMES utf8mb4;
USE cyxy;

-- 建表脚本早期版本把 menu_type 默认值写成 1（旧语义「1菜单」），
-- 这里把已存在的表也对齐成 0（新语义「0显示」）。重复执行无害。
ALTER TABLE `sys_menu` ALTER COLUMN `menu_type` SET DEFAULT 0;

-- ---------------------------------------------------------------------
-- 1. 内置超级管理员角色
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_role`
  (`id`, `role_code`, `role_name`, `is_builtin`, `sort_no`, `status`, `remark`)
VALUES
  (1, 'SUPER_ADMIN', '超级管理员', 1, 0, 1, '内置角色：永远全放行，不可删改、不参与菜单勾选');

-- ---------------------------------------------------------------------
-- 2. 超管账号（密码 123456，BCrypt 密文由项目依赖实际生成并验证过）
--    用 INSERT IGNORE：重跑脚本绝不能把用户后来改过的密码重置回 123456。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_user`
  (`id`, `username`, `password_hash`, `real_name`, `status`, `remark`)
VALUES
  (1, 'macong', '$2a$10$2mrfNQxRA7/iISFpQyTfleUPBukFhQAGiyxLbGYVCOnvNPglE7hb2',
   '系统管理员', 1, '内置超管账号，请登录后尽快修改密码');

-- ---------------------------------------------------------------------
-- 3. 超管绑角色
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- ---------------------------------------------------------------------
-- 4. 菜单树
--    route_name 以 SYSTEM_ 开头的会被 RbacQueryService 识别成「系统管理组」，
--    对非超管角色一律不下发，即使超管在角色页误勾也不会生效。
-- ---------------------------------------------------------------------
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `title`, `route_name`, `url`, `icon`,
   `menu_type`, `sort_no`, `visible`, `is_builtin`, `write_api_patterns`)
VALUES
  -- 一级菜单 1~5：sort_no 决定侧边栏顺序，首页必须最小
  (1,  0, '首页',       'HOME',          '/home',  'icon-shouye',    0, 10, 1, 1, NULL),
  (2,  0, '系统管理',   'SYSTEM_ROOT',   '',       'icon-settings',  0, 20, 1, 1, NULL),
  (3,  0, '产品管理',   'PRODUCT_ROOT',  '',       'icon-chanpin',   0, 30, 1, 1, NULL),
  (4,  0, '运营工具',   'TOOLS_ROOT',    '',       'icon-tools',     0, 40, 1, 1, NULL),
  (5,  0, '参数列表',   'PARAMS_ROOT',   '',       'icon-params',    0, 50, 1, 1, NULL),

  -- 系统管理：仅超管可见；三个子页的 url 要与 router/route.js 注册的完全一致
  (6,  2, '用户管理',   'SYSTEM_USER',   '/system/user',   'icon-system-user',  0, 10, 1, 1, NULL),
  (7,  2, '角色管理',   'SYSTEM_ROLE',   '/system/role',   'icon-system-role',  0, 20, 1, 1, NULL),
  (8,  2, '菜单管理',   'SYSTEM_MENU',   '/system/menu',   'icon-system-menu',  0, 30, 1, 1, NULL),

  -- 产品管理
  (9,  3, '产品列表',   'PRODUCT_LIST',  '/product/list',  'icon-product-list', 0, 10, 1, 1,
        '/api/product/update,/api/product/delete,/api/product/confirm-delivery,/api/product/cancel-delivery'),
  -- 隐藏功能页：menu_type=1，不显示在侧边栏，但必须下发，
  -- 否则 router/index.js 的守卫会把 /product/detail 判成 404。
  -- 权限跟随父菜单「产品列表」，不要求角色单独勾。
  (10, 9, '产品详情',   'PRODUCT_DETAIL','/product/detail', NULL,               1, 30, 1, 1, NULL),
  (11, 3, '录入产品信息','PRODUCT_CREATE','/product/create','icon-product-add', 0, 20, 1, 1,
        '/api/product/create,/api/product/import'),

  -- 运营工具：只拦 save；calc 是 POST 但只试算不落库，只读角色应该照样能算
  (12, 4, '利润测算',   'TOOLS_PROFIT',  '/tools/profit-calculator', NULL,    0, 10, 1, 1,
        '/api/profit/save'),

  -- 参数列表（9 个页全是只读展示，无写接口，角色页只读开关会置灰）
  (13, 5, '佣金费率',       'PARAMS_COMMISSION', '/params/commission',       'icon-commission', 0, 10, 1, 1, NULL),
  (14, 5, '退货阈值',       'PARAMS_RETURN',     '/params/return-threshold', 'icon-return',     0, 20, 1, 1, NULL),
  (15, 5, '汇率变化',       'PARAMS_EXCHANGE',   '/params/exchange-rate',    'icon-exchange',   0, 30, 1, 1, NULL),
  (16, 5, '尺寸标准',       'PARAMS_SIZE',       '/params/size-tier',        'icon-size',       0, 40, 1, 1, NULL),
  (17, 5, '尺寸收费标准',   'PARAMS_RATE',       '/params/fba-rate',         'icon-rate',       0, 50, 1, 1, NULL),
  (18, 5, '头程方式',       'PARAMS_FIRST_LEG',  '/params/first-leg',        'icon-truck',      0, 60, 1, 1, NULL),
  (19, 5, '超大件费率',     'PARAMS_STEP_RATE',  '/params/step-rate',        'icon-oversize',   0, 70, 1, 1, NULL),
  (20, 5, '全局参数',       'PARAMS_SYS_CONFIG', '/params/sys-config',       'icon-settings',   0, 80, 1, 1, NULL),
  -- 2026-11-10 新增：尾程退货费展示页（7 分组），纯只读
  (21, 5, '尾程退货费',     'PARAMS_RETURN_FEE', '/params/return-fee',       'icon-return-fee', 0, 90, 1, 1, NULL)
ON DUPLICATE KEY UPDATE
  -- 只修正技术字段；title / icon / sort_no / visible 允许管理员在页面上改，重跑不覆盖
  `parent_id`          = VALUES(`parent_id`),
  `route_name`         = VALUES(`route_name`),
  `url`                = VALUES(`url`),
  `menu_type`          = VALUES(`menu_type`),
  `is_builtin`         = VALUES(`is_builtin`),
  `write_api_patterns` = VALUES(`write_api_patterns`);

-- ---------------------------------------------------------------------
-- 5. 自检：正常应看到 1 个超管角色、1 个账号、21 条菜单（全新环境全部启用）、
--          3 个菜单带写接口清单。
-- ---------------------------------------------------------------------
SELECT 'sys_role' AS tbl, COUNT(*) AS cnt FROM `sys_role`
UNION ALL SELECT 'sys_user', COUNT(*) FROM `sys_user`
UNION ALL SELECT 'sys_user_role', COUNT(*) FROM `sys_user_role`
UNION ALL SELECT 'sys_menu', COUNT(*) FROM `sys_menu`
UNION ALL SELECT 'sys_menu(visible=0)', COUNT(*) FROM `sys_menu` WHERE `visible` = 0
UNION ALL SELECT 'sys_menu(with_write_api)', COUNT(*) FROM `sys_menu` WHERE `write_api_patterns` IS NOT NULL;

-- =====================================================================
-- 历史库升级（2026-11-05 之前跑过本脚本的机器）：
--   1) 菜单 id / 顺序 / 「工具」改名 → docs/data/2026-11-05_menu_reorder.sql
--   2) 当时 4 条 SYSTEM_ 菜单是 visible=0，而本脚本的 ON DUPLICATE KEY UPDATE
--      刻意不含 visible（避免覆盖管理员在页面上的启停设置），所以重跑本脚本
--      不会把它们改回启用 → docs/data/2026-11-05_system_menu_enable.sql
-- =====================================================================
