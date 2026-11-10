-- ============================================================================
-- 参数列表新增「尾程退货费」展示页（2026-11-10）
--
-- ⚠️ 本项目菜单由后端 sys_menu 下发，光在前端加路由 / menu.json 不会显示。
--    且普通用户角色必须在 sys_role_menu 里有记录才看得到；
--    超管 is_builtin=1 由拦截器直接全放行，不写关联表（见 rbac init 脚本说明）。
--
-- 幂等：菜单用 ON DUPLICATE KEY UPDATE（只更新技术字段，不覆盖管理员在页面上
--       改过的 title / icon / sort_no / visible），角色关联用 INSERT IGNORE。
-- ============================================================================

INSERT INTO `sys_menu`
  (`id`, `parent_id`, `title`, `route_name`, `url`, `icon`,
   `menu_type`, `sort_no`, `visible`, `is_builtin`, `write_api_patterns`)
VALUES
  (21, 5, '尾程退货费', 'PARAMS_RETURN_FEE', '/params/return-fee', 'icon-return-fee',
   0, 90, 1, 1, NULL)
ON DUPLICATE KEY UPDATE
  `parent_id`          = VALUES(`parent_id`),
  `route_name`         = VALUES(`route_name`),
  `url`                = VALUES(`url`),
  `menu_type`          = VALUES(`menu_type`),
  `is_builtin`         = VALUES(`is_builtin`),
  `write_api_patterns` = VALUES(`write_api_patterns`);

-- 所有非内置、启用中的角色都要能看见这一页；超管不需要写（全放行）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `perm_type`)
SELECT `id`, 21, 1 FROM `sys_role`
WHERE `is_builtin` = 0 AND `status` = 1 AND `is_deleted` = 0;

-- 验证：菜单存在且已授权给普通用户
SELECT `id`, `title`, `route_name`, `url`, `icon`, `sort_no`, `visible`
FROM `sys_menu` WHERE `id` = 21;
SELECT `role_id`, `menu_id`, `perm_type` FROM `sys_role_menu` WHERE `menu_id` = 21;
