-- =====================================================================
-- 2026-11-05 系统管理三个前端页面上线：启用菜单 + 补图标
-- 依赖：2026-11-04_rbac_tables.sql、2026-11-04_rbac_init.sql
-- ---------------------------------------------------------------------
-- 背景：初始化脚本早期版本把 4 条 SYSTEM_ 菜单写成 visible=0（当时前端
--       三个页面还没做，怕超管点到跳 404 的入口）。现在页面已实现并注册路由，
--       需要把它们放出来。
--
-- ⚠️ 为什么不直接重跑 2026-11-04_rbac_init.sql：
--      它的 ON DUPLICATE KEY UPDATE 刻意不含 visible / icon（避免覆盖管理员
--      在菜单管理页改过的启停和图标），所以对已经存在的行重跑是无效的。
--      必须单独 UPDATE 一次，这就是本脚本存在的原因。
--
-- 幂等：全部带条件，重复执行不会覆盖管理员后来做的设置。
-- =====================================================================

SET NAMES utf8mb4;
USE cyxy;

-- 1. 启用系统管理一级目录 + 三个页面。
--    只改当前停用的行：管理员如果主动停用过某个页面，重跑本脚本不会强行打开。
UPDATE `sys_menu`
SET `visible` = 1
WHERE `route_name` LIKE 'SYSTEM\_%' AND `visible` = 0;

-- 2. 补上三个页面的图标（早期版本 icon 是 NULL）。
--    同样只在空值时填，避免覆盖管理员在菜单管理页里换过的图标。
UPDATE `sys_menu` SET `icon` = 'icon-system-user'
WHERE `route_name` = 'SYSTEM_USER' AND (`icon` IS NULL OR `icon` = '');
UPDATE `sys_menu` SET `icon` = 'icon-system-role'
WHERE `route_name` = 'SYSTEM_ROLE' AND (`icon` IS NULL OR `icon` = '');
UPDATE `sys_menu` SET `icon` = 'icon-system-menu'
WHERE `route_name` = 'SYSTEM_MENU' AND (`icon` IS NULL OR `icon` = '');

-- 3. 自检：应看到 4 行，visible 全为 1，三个子页各带自己的图标。
--    url 必须与前端 router/route.js 注册的 /system/{user,role,menu} 一致，
--    否则后端下发了菜单、前端路由守卫也会判成 404。
SELECT `id`, `route_name`, `title`, `url`, `icon`, `visible`, `is_builtin`
FROM `sys_menu`
WHERE `route_name` LIKE 'SYSTEM\_%'
ORDER BY `id`;
