-- =====================================================================
-- 2026-11-10 运营工具菜单改名：「利润计算器」→「利润测算」
-- ---------------------------------------------------------------------
-- 菜单树由后端 sys_menu 表经 GET /api/auth/menus 下发，前端只负责渲染，
-- 所以改显示名必须落到这张表；光改前端文件不会生效。
-- 前端 router/menu.json 是静态兜底（当前无人 import），同步改一份避免两处漂移。
--
-- 只改 title 一列：id / route_name / url / 排序 / 可见性 / 写接口绑定全部不动，
-- 角色与菜单的关联关系（sys_role_menu）也不受任何影响。
-- 脚本幂等：按 route_name 定位，重复执行结果一致。
-- 执行时请用 utf8mb4 客户端字符集，避免中文乱码：
--   mysql --default-character-set=utf8mb4 -uroot -p cyxy < 本文件
-- =====================================================================

USE cyxy;

UPDATE sys_menu
SET title = '利润测算'
WHERE route_name = 'TOOLS_PROFIT' AND is_deleted = 0;

-- 验证：应只剩一行，且 title 已改、路由未变
SELECT id, title, route_name, url, sort_no, visible
FROM sys_menu
WHERE route_name = 'TOOLS_PROFIT';
