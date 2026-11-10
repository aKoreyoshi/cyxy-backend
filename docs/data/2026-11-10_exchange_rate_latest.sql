-- ============================================================================
-- 2026-11-10 全局参数「默认汇率」→「最新汇率」语义升级
--
-- 职责分工（用户 2026-11-10 定案，改动前务必先读懂）：
--   dict_exchange_rate  时序表，只记录汇率「变化」，一天一条，供 ECharts 趋势图；
--                       唯一键 (currency_pair, rate_date) 天然保证重复获取覆盖。
--   sys_config.DEFAULT_EXCHANGE_RATE
--                       当前生效汇率，是【所有业务读取的唯一入口】。
--                       利润测算、prefill 都从这里拿，不再各自去时序表 ORDER BY 取最新。
--   汇率同步任务         一次事务里同时写上面两处，保证趋势图与测算口径永不打架。
--
-- ⚠️ 只改 config_name / remark，config_key 故意保留 DEFAULT_EXCHANGE_RATE：
--    config_key 是程序标识符，Java / 前端 / 生成脚本 / 测试都按它查，改名收益为零、
--    风险却很大（漏改一处就是查不到配置、静默走兜底 6.68）。用户看到的中文名才是
--    「最新汇率」。旧的 remark 写「实际以汇率表/接口为准」，语义正好相反，必须一起改。
--
-- ⚠️ 不改 config_value：当前仍是基线 6.68，等汇率同步任务真正拉到新值才更新，
--    否则会立刻把利润回归基准（￥95.13 / ￥31.72，基于 6.68）算崩。
--
-- ⚠️ 不把 POST /api/exchange-rate/refresh 登记进 sys_menu.write_api_patterns：
--    它只做「系统级数据同步」，不增删改任何业务数据，性质等同 /api/profit/calc
--    （POST 但只算不写库）。登记后只读角色会被拦截器挡成 403，反而用不了刷新按钮。
--
-- 幂等：纯 UPDATE，重复执行结果一致；不含 INSERT，不会覆盖管理员改过的值。
-- ============================================================================

SET NAMES utf8mb4;
USE cyxy;

UPDATE `sys_config`
SET `config_name` = '最新汇率',
    `remark`      = 'USD/CNY 当前生效汇率；业务读取的唯一入口。由汇率同步任务在写入 dict_exchange_rate 最新一天记录时同步更新，历史变化只留在时序表里'
WHERE `config_key` = 'DEFAULT_EXCHANGE_RATE';

-- ---------------------------------------------------------------------
-- 自检：正常应看到 config_name = 最新汇率、config_value 仍是 6.68
-- ---------------------------------------------------------------------
SELECT `config_key`, `config_value`, `config_name`, `remark`
FROM `sys_config`
WHERE `config_key` = 'DEFAULT_EXCHANGE_RATE';
