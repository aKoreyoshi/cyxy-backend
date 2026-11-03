-- =============================================================
-- 汇率基线（源自 Excel 汇率附件，更新时间 2026-10-30 15:43:12）
-- 由 generate_dict_sql.py 从前端 dict.js 自动生成，请勿手工编辑
-- 幂等：带 ON DUPLICATE KEY UPDATE，可重复执行
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO dict_exchange_rate
    (currency_pair, rate, source, provider, rate_date, fetched_at, remark)
VALUES
    ('USD/CNY', 6.680000, 'MANUAL', 'excel_attachment', '2026-10-30', '2026-10-30 15:43:12', '初始化基线：源自 Excel《汇率附件》(6.68)。接入实时汇率接口后以 API 取值为准')
ON DUPLICATE KEY UPDATE
    rate = VALUES(rate),
    source = VALUES(source),
    provider = VALUES(provider),
    fetched_at = VALUES(fetched_at),
    remark = VALUES(remark);
