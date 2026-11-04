-- =====================================================================
-- 2026-11-04 权限控制一期：新建 RBAC 5 张表
-- ---------------------------------------------------------------------
-- 范围：只建表，不导初始化数据（超管角色 / 超管账号 / 菜单树另开脚本）。
--       本脚本只在本地库执行；上云前需重放一次。
--
-- 设计约定：
--   1) 5 张表：sys_user / sys_role / sys_menu / sys_user_role / sys_role_menu
--      不另建 token 表 —— 令牌落在 sys_user.token + token_expire_time，
--      一人一令牌，异地登录顶掉旧令牌。
--   2) 权限粒度到「菜单级读写」，不做按钮级：
--      sys_role_menu.perm_type = 1 可编辑 / 0 只读；
--      sys_menu.write_api_patterns 存该菜单下需要拦截的写接口路径（逗号分隔），
--      只读角色命中即 403。不按 HTTP 方法猜写操作（profit/calc 是 POST 但不写库）。
--   3) 菜单一期不开放「凭空新建可点击页面」：无对应前端 .vue 会白屏。
--      菜单管理只做改名 / 排序 / 启停 / 换上级 / 新建目录，
--      route_name 用于把菜单映射到已注册的前端路由。
--   4) 内置超级管理员（sys_role.is_builtin=1）不参与菜单勾选、永远全放行、
--      不可删改；系统管理一级菜单仅超管可见。
--   5) 沿用项目规范：软删 is_deleted、无物理外键、时间列 DATETIME(3)。
--      关联表直接物理删关联行，不加 is_deleted。
--
-- 幂等：所有 CREATE 均带 IF NOT EXISTS，可重复执行。
-- =====================================================================

SET NAMES utf8mb4;
USE cyxy;

-- ---------------------------------------------------------------------
-- 1. 系统用户
--    唯一索引只含 username：软删后同名账号不可重建，如需复用请直接恢复
--    （status 置 1 / is_deleted 置 0），3 人规模下够用。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `username`            VARCHAR(64)      NOT NULL                COMMENT '登录账号，唯一',
  `password_hash`       VARCHAR(100)     NOT NULL                COMMENT 'BCrypt 密文，禁止明文',
  `real_name`           VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '姓名 / 昵称',
  `status`              TINYINT          NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  `token`               VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '当前有效登录令牌；一人一令牌，异地登录顶掉旧令牌',
  `token_expire_time`   DATETIME(3)      NULL     DEFAULT NULL   COMMENT '令牌过期时间，NULL 视为已失效',
  `last_login_time`     DATETIME(3)      NULL     DEFAULT NULL   COMMENT '最后登录时间',
  `remark`              VARCHAR(255)     NULL     DEFAULT NULL   COMMENT '备注',
  `created_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0      COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';


-- ---------------------------------------------------------------------
-- 2. 角色
--    role_code = SUPER_ADMIN 为内置超管：不可删、权限不可改、永远全放行。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `role_code`           VARCHAR(64)      NOT NULL                COMMENT '角色编码，如 SUPER_ADMIN / OPERATOR / VIEWER',
  `role_name`           VARCHAR(64)      NOT NULL                COMMENT '角色名称',
  `is_builtin`          TINYINT          NOT NULL DEFAULT 0      COMMENT '内置：1 超级管理员（不可删改、不参与菜单勾选、永远全放行）',
  `sort_no`             INT              NOT NULL DEFAULT 0      COMMENT '排序号',
  `status`              TINYINT          NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
  `remark`              VARCHAR(255)     NULL     DEFAULT NULL   COMMENT '备注',
  `created_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0      COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色';


-- ---------------------------------------------------------------------
-- 3. 菜单（同时作为权限项登记表）
--    parent_id = 0 为一级菜单；是否目录看 url 是否为空。
--    ⚠️ menu_type 沿用前端 V3S 模板语义：0 显示在侧边栏 / 1 隐藏功能页，
--       不是「0目录/1菜单」。前端 menuConfig.js 的 menuValue = "0"，
--       useLayout.js 用它过滤侧边栏菜单；按字面反转会让侧边栏只剩空目录。
--       visible 是启停开关：0 停用的菜单不下发给任何角色（含超管）。
--    write_api_patterns 示例：
--      产品管理  /api/product/create,/api/product/update,/api/product/delete,/api/product/import
--      利润计算  /api/profit/save
--    无写接口的菜单（首页 / 参数列表 / 字典展示）保持 NULL，角色管理页
--    对应「只读」开关置灰并显示「无写操作」。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `parent_id`           BIGINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '上级菜单 id，0 = 一级菜单',
  `title`               VARCHAR(64)      NOT NULL                COMMENT '菜单名称',
  `route_name`          VARCHAR(64)      NULL     DEFAULT NULL   COMMENT '前端路由名，用于菜单 ↔ 已注册路由映射；目录为空',
  `url`                 VARCHAR(128)     NULL     DEFAULT NULL   COMMENT '前端 hash 路由 path；目录为空',
  `icon`                VARCHAR(64)      NULL     DEFAULT NULL   COMMENT 'iconfont 类名',
  `menu_type`           TINYINT          NOT NULL DEFAULT 0      COMMENT '0显示在侧边栏 1隐藏功能页（对齐前端 menuConfig.menuValue=0）',
  `sort_no`             INT              NOT NULL DEFAULT 0      COMMENT '排序号，升序；首页必须最小，前端默认跳第一个有 url 的菜单',
  `visible`             TINYINT          NOT NULL DEFAULT 1      COMMENT '1启用 0停用；停用的菜单不下发给任何角色',
  `is_builtin`          TINYINT          NOT NULL DEFAULT 0      COMMENT '1内置菜单（一期登记现有页面，不可删）',
  `write_api_patterns`  VARCHAR(512)     NULL     DEFAULT NULL   COMMENT '该菜单下需拦截的写接口路径，逗号分隔；角色对本菜单为只读时命中即 403',
  `created_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted`          TINYINT          NOT NULL DEFAULT 0      COMMENT '软删：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  KEY `idx_sys_menu_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统菜单与权限项';


-- ---------------------------------------------------------------------
-- 4. 用户-角色关联
--    界面上一个用户只选一个角色；表按多对多预留，以后扩展不用改结构。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id`             BIGINT UNSIGNED  NOT NULL                COMMENT 'sys_user.id',
  `role_id`             BIGINT UNSIGNED  NOT NULL                COMMENT 'sys_role.id',
  `created_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联（界面单选，表按多对多预留）';


-- ---------------------------------------------------------------------
-- 5. 角色-菜单权限
--    perm_type：1 可编辑 / 0 只读。
--    超管角色不写这张表（永远全放行）；不在表里的菜单 = 该角色不可见。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id`             BIGINT UNSIGNED  NOT NULL                COMMENT 'sys_role.id',
  `menu_id`             BIGINT UNSIGNED  NOT NULL                COMMENT 'sys_menu.id',
  `perm_type`           TINYINT          NOT NULL DEFAULT 1      COMMENT '1可编辑 0只读；只读时命中 sys_menu.write_api_patterns 的写接口即 403',
  `created_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`role_id`, `menu_id`),
  KEY `idx_sys_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单权限（菜单级读写，不做按钮级）';

-- =====================================================================
-- 本脚本结束。下一步：初始化数据脚本（超管角色 / 超管账号 / 菜单树导入）。
-- =====================================================================
