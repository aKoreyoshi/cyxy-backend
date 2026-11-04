package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年11月04日, 16:55:45
 */

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity{

    /** 角色编码，如 SUPER_ADMIN，库上唯一 */
    private String roleCode;

    private String roleName;

    /** 1 内置超管：禁止删除、禁止改权限 */
    private Integer isBuiltin;

    private Integer sortNo;
    private Integer status;
    private String remark;

}