package com.cyxy.model.dto;

import lombok.Data;

/**
 * @author: Koreyoshi
 * @description: 列表查询参数。注意：GET query 绑定不走 Jackson，字段名必须照前端写成 camelCase
 *               （pageNumber / nameCn / categoryCode / sortField ...），别用 snake_case。
 * @version: 1.0
 * @date: 2026年11月02日, 22:07:28
 */

@Data
public class ProductQueryDTO {

    private Integer pageNumber = 1;
    private Integer pageSize = 10;
    private String nameCn;
    private String productNo;
    private String categoryCode;
    private String status;
    private String sortField;
    private String sortOrder;   // Element Plus 传 "ascending" / "descending"
}