package com.cyxy.model.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 用户管理：新建 / 修改用户。
 *
 * 一个 DTO 兼两用，靠 id 区分：id 为空 = 新建，id 有值 = 修改。
 * password 为空在「修改」时表示不改动原密码；「新建」时必填。
 *
 * roleId 是单选：界面上一个人只选一个角色，表虽然按多对多建，一期不做多选。
 *
 * ⚠️ 全部 DTO 都加 @JsonNaming(LowerCamelCase)：application.yml 配了全局
 *    SNAKE_CASE，不加的话前端传 roleId 会被对译成 role_id，后端收不到值。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SysUserSaveDTO(
        Long id,
        String username,
        String password,
        String realName,
        Long roleId,
        Integer status,
        String remark
) {
}
