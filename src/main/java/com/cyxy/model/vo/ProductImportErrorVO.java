package com.cyxy.model.vo;

/**
 * 导入失败明细：定位到「第几行的哪一列出的问题」。
 *
 * <p>一条产品可能同时有多个字段出错，所以错误是「一行一条」而不是「一行一条汇总」——
 * 用户改起来更直接：看到哪行哪列，打开 Excel 直接定位。</p>
 *
 * @param rowNum    Excel 里的行号（与用户看到的左侧行号一致，表头算第 1 行，所以数据从 2 起）
 * @param productNo 产品编号，出错行里编号也读不出来时为空字符串
 * @param column    出错列名（表头文字，去掉 * 后）
 * @param message   具体原因，直接给用户看，不写代码术语
 */
public record ProductImportErrorVO(int rowNum, String productNo, String column, String message) {
}
