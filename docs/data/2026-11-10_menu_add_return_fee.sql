-- ============================================================================
-- 参数列表新增「尾程退货费」展示页（2026-11-10，2026-11-11 上云前修正）
--
-- ⚠️ 本项目菜单由后端 sys_menu 下发，光在前端加路由 / menu.json 不会显示。
--    且普通用户角色必须在 sys_role_menu 里有记录才看得到；
--    超管 is_builtin=1 由拦截器直接全放行，不写关联表（见 rbac init 脚本说明）。
--
-- ⚠️ 2026-11-11 上云前实测发现：线上 id=21 已被一条【软删的测试菜单】占用
--    （title='1'、route_name=NULL、is_deleted=1）。初版脚本写死 id=21 并用
--    ON DUPLICATE KEY UPDATE，会改动这条历史记录；更糟的是脚本为保护管理员
--    在页面上改过的显示配置，刻意不更新 title / icon / sort_no / visible /
--    is_deleted，于是新菜单仍然是软删状态、名字还是 '1'，页面根本不会出现。
--    所以改为【不写死 id、按 route_name 幂等定位】：id 交给 AUTO_INCREMENT，
--    全新空库由 2026-11-04_rbac_init.sql 统一建 id=21；已有环境（尤其 id 被占用）
--    由本脚本补建，绝不修改任何已存在的菜单行，存量数据一条不动。
--
-- ⚠️ sys_menu 没有 route_name 唯一索引，不能靠 ON DUPLICATE KEY 判重。
--    这里用「先查计数存入变量 + FROM DUAL 控制插入」：既保证重复执行不新增，
--    也避开 INSERT ... SELECT 里用 NOT EXISTS 子查询引用目标表自身时，
--    部分 MySQL 版本会报 1093（can't specify target table for update in FROM clause）。
--    sys_role_menu 有联合主键 (role_id, menu_id)，INSERT IGNORE 天然幂等。
--
-- 执行时请显式指定库（本脚本不写 USE，便于在同一连接里连跑多个迁移）：
--   mysql --default-character-set=utf8mb4 -uroot -p cyxy < 本文件
-- ============================================================================

SET NAMES utf8mb4;

-- 只要已经存在一条「未软删且路由正确」的菜单，就不再插入。
-- 软删过的同名路由会被当成不存在，重新补建一条有效菜单（历史软删行保留）。
SET @return_fee_menu_exists = (
  SELECT COUNT(*) FROM `sys_menu`
  WHERE `route_name` = 'PARAMS_RETURN_FEE' AND `is_deleted` = 0
);

INSERT INTO `sys_menu`
  (`parent_id`, `title`, `route_name`, `url`, `icon`,
   `menu_type`, `sort_no`, `visible`, `is_builtin`, `write_api_patterns`)
SELECT
  5, '尾程退货费', 'PARAMS_RETURN_FEE', '/params/return-fee', 'icon-return-fee',
  0, 90, 1, 1, NULL
FROM DUAL
WHERE @return_fee_menu_exists = 0;

-- 所有非内置、启用中的角色都要能看见这一页；超管不需要写（全放行）。
-- 按 route_name 反查真实 id，不再依赖固定 21；联合主键冲突由 INSERT IGNORE 吃掉。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `perm_type`)
SELECT r.`id`, m.`id`, 1
FROM `sys_role` r
CROSS JOIN `sys_menu` m
WHERE m.`route_name` = 'PARAMS_RETURN_FEE' AND m.`is_deleted` = 0
  AND r.`is_builtin` = 0 AND r.`status` = 1 AND r.`is_deleted` = 0;

-- 验证：菜单存在、未被软删、已授权给普通用户；顺带看真实 id（线上应为 22）
SELECT `id`, `title`, `route_name`, `url`, `icon`, `sort_no`, `visible`, `is_deleted`
FROM `sys_menu` WHERE `route_name` = 'PARAMS_RETURN_FEE';

SELECT `role_id`, `menu_id`, `perm_type` FROM `sys_role_menu`
WHERE `menu_id` IN (
  SELECT `id` FROM `sys_menu` WHERE `route_name` = 'PARAMS_RETURN_FEE'
);
