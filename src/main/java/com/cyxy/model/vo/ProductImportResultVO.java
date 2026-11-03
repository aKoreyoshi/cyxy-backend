package com.cyxy.model.vo;

import java.util.List;

/**
 * 导入结果。
 *
 * <p>策略是「整批成功或整批失败」：只要有一行不合规，全批都不入库，
 * 所以 successCount 只会是 0 或 total，两者必居其一。
 * failCount 与 errors 用于把「哪行哪列错了」摆给用户，改完重传即可。</p>
 *
 * @param total        解析到的数据行数（不含表头与空行）
 * @param successCount 成功入库条数
 * @param failCount    失败条数
 * @param errors       失败明细，最多返回全部（批量导入一般几十行，不需要截断）
 */
public record ProductImportResultVO(
        int total,
        int successCount,
        int failCount,
        List<ProductImportErrorVO> errors
) {
    /** 全部成功 */
    public static ProductImportResultVO allOk(int total) {
        return new ProductImportResultVO(total, total, 0, List.of());
    }

    /** 整批失败（一行都没入库） */
    public static ProductImportResultVO failed(int total, List<ProductImportErrorVO> errors) {
        return new ProductImportResultVO(total, 0, errors.size(), errors);
    }
}
