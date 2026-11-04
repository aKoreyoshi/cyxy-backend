-- =====================================================================
-- 2026-11-05 菜单整理：顺序 / 命名 / id 重排（【历史库升级】专用）
-- 依赖：2026-11-04_rbac_tables.sql（5 张表已存在且有菜单数据）
-- ---------------------------------------------------------------------
-- 背景：权限改造后侧边栏菜单由后端 sys_menu 表下发，早期初始化脚本给的 id
--       是 1 / 10~13 / 20~21 / 30~38 / 40~43 这种跳号。清掉三组模板示例
--       页面后重新整理：一级菜单 1~5、子页 6~20，顺序为
--       首页 → 系统管理 → 产品管理 → 运营工具 → 参数列表，「工具」改名「运营工具」。
--
-- ⚠️ 全新空库（云上首次部署）不要用本脚本，直接跑 2026-11-04_rbac_init.sql
--      即可，它已经按新方案写好。sys_menu 只有主键 id、route_name 没有唯一
--      索引，历史库若直接重跑 init 脚本会按新 id 再插一份重复菜单。
--
-- 难点：id 是主键，新旧值互相重叠（旧 10 要变成 3，而新 3 可能正被别的行占着），
--       逐条 UPDATE 会撞主键。做法是把所有 id 先整体挪到 +1000 的临时高位区，
--       再按 route_name 设定目标值 —— 目标值 1~20 与高位区不可能冲突。
--       每一步都按 route_name 而不是旧 id 定位，所以重复执行结果完全一致（幂等）。
--
-- 权限不丢：sys_role_menu 只存 menu_id，重排后会全部失效。先把授权按
--       route_name 备份到临时表，重排完再按新 id 写回，角色勾选与只读标记都保留。
--
-- 注意：sort_no 会按本方案重置；如果管理员曾在「菜单管理」页手动调过排序或
--       改过「运营工具」以外的标题，重跑本脚本会覆盖那部分手工设置。
-- =====================================================================

SET NAMES utf8mb4;
USE cyxy;

-- ---------------------------------------------------------------------
-- 1. 备份角色-菜单授权：记住 route_name，而不是记住 id
--    顺带 JOIN sys_role，角色已被删掉的孤儿授权不备份（本来也没意义）。
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_rbac_role_menu;
CREATE TEMPORARY TABLE tmp_rbac_role_menu AS
SELECT rm.`role_id`, m.`route_name`, rm.`perm_type`, rm.`created_time`, rm.`updated_time`
FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
JOIN `sys_role` r ON r.`id` = rm.`role_id`;

DELETE FROM `sys_role_menu`;

-- ---------------------------------------------------------------------
-- 2. 目标映射表：一条菜单一行，和 2026-11-04_rbac_init.sql 逐条对应
--    new_parent 用【新】id，不依赖旧编号，所以可重复执行。
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_menu_target;
CREATE TEMPORARY TABLE tmp_menu_target (
  `route_name` VARCHAR(64) NOT NULL PRIMARY KEY,
  `new_id`     BIGINT UNSIGNED NOT NULL,
  `new_parent` BIGINT UNSIGNED NOT NULL,
  `new_sort`   INT NOT NULL
) DEFAULT CHARSET = utf8mb4;

INSERT INTO tmp_menu_target (`route_name`, `new_id`, `new_parent`, `new_sort`) VALUES
  ('HOME',               1,  0, 10),   -- 首页必须是一级里 sort_no 最小的：
                                       -- router/index.js 跳「第一个有 url 的菜单」
  ('SYSTEM_ROOT',        2,  0, 20),
  ('PRODUCT_ROOT',       3,  0, 30),
  ('TOOLS_ROOT',         4,  0, 40),
  ('PARAMS_ROOT',        5,  0, 50),
  ('SYSTEM_USER',        6,  2, 10),
  ('SYSTEM_ROLE',        7,  2, 20),
  ('SYSTEM_MENU',        8,  2, 30),
  ('PRODUCT_LIST',       9,  3, 10),
  ('PRODUCT_DETAIL',    10,  9, 30),   -- 隐藏页仍挂在「产品列表」下，权限跟随父菜单
  ('PRODUCT_CREATE',    11,  3, 20),
  ('TOOLS_PROFIT',      12,  4, 10),
  ('PARAMS_COMMISSION', 13,  5, 10),
  ('PARAMS_RETURN',     14,  5, 20),
  ('PARAMS_EXCHANGE',   15,  5, 30),
  ('PARAMS_SIZE',       16,  5, 40),
  ('PARAMS_RATE',       17,  5, 50),
  ('PARAMS_FIRST_LEG',  18,  5, 60),
  ('PARAMS_STEP_RATE',  19,  5, 70),
  ('PARAMS_SYS_CONFIG', 20,  5, 80);

-- ---------------------------------------------------------------------
-- 3. 整体挪到高位区，避开新旧主键冲突。
--    parent_id 同步偏移只是为了让中间状态不自相矛盾，第 5 步会整体覆盖。
-- ---------------------------------------------------------------------
UPDATE `sys_menu` SET `id` = `id` + 1000;
UPDATE `sys_menu` SET `parent_id` = `parent_id` + 1000 WHERE `parent_id` <> 0;

-- ---------------------------------------------------------------------
-- 4. 按映射设定目标 id（只动未软删的行；软删行留在高位区不影响使用）
-- ---------------------------------------------------------------------
UPDATE `sys_menu` m
JOIN tmp_menu_target t ON m.`route_name` = t.`route_name`
SET m.`id` = t.`new_id`
WHERE m.`is_deleted` = 0;

-- ---------------------------------------------------------------------
-- 5. 按映射重设 parent_id 与 sort_no
-- ---------------------------------------------------------------------
UPDATE `sys_menu` m
JOIN tmp_menu_target t ON m.`route_name` = t.`route_name`
SET m.`parent_id` = t.`new_parent`,
    m.`sort_no`   = t.`new_sort`
WHERE m.`is_deleted` = 0;

-- ---------------------------------------------------------------------
-- 6. 改名：两字的「工具」补齐成四字，与其它一级菜单对齐
-- ---------------------------------------------------------------------
UPDATE `sys_menu`
SET `title` = '运营工具'
WHERE `route_name` = 'TOOLS_ROOT' AND `is_deleted` = 0;

-- ---------------------------------------------------------------------
-- 7. 恢复角色授权：用 route_name 反查新 id
-- ---------------------------------------------------------------------
INSERT INTO `sys_role_menu`
  (`role_id`, `menu_id`, `perm_type`, `created_time`, `updated_time`)
SELECT b.`role_id`, m.`id`, b.`perm_type`, b.`created_time`, b.`updated_time`
FROM tmp_rbac_role_menu b
JOIN `sys_menu` m ON m.`route_name` = b.`route_name`;

DROP TEMPORARY TABLE IF EXISTS tmp_rbac_role_menu;
DROP TEMPORARY TABLE IF EXISTS tmp_menu_target;

-- ---------------------------------------------------------------------
-- 8. 新建菜单从 21 开始，别让 AUTO_INCREMENT 还停在旧布局的 45
--    （若库里存在更高 id 的自定义菜单，MySQL 会自动取 max+1，不会冲突）
-- ---------------------------------------------------------------------
ALTER TABLE `sys_menu` AUTO_INCREMENT = 21;

-- ---------------------------------------------------------------------
-- 9. 自检：应为 20 条、id 1~20 连续、一级菜单顺序符合预期、3 条写接口清单
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS menu_total, MIN(`id`) AS min_id, MAX(`id`) AS max_id,
       SUM(`is_deleted`) AS deleted_rows
FROM `sys_menu`;

SELECT `id`, `parent_id`, `title`, `route_name`, `sort_no`
FROM `sys_menu`
WHERE `parent_id` = 0
ORDER BY `sort_no`;

SELECT `route_name`, `write_api_patterns`
FROM `sys_menu`
WHERE `write_api_patterns` IS NOT NULL
ORDER BY `id`;

SELECT COUNT(*) AS role_menu_rows FROM `sys_role_menu`;
