package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.SysUserMapper;
import com.cyxy.model.dto.LoginDTO;
import com.cyxy.model.entity.SysRole;
import com.cyxy.model.entity.SysUser;
import com.cyxy.model.vo.CurrentUserVO;
import com.cyxy.model.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 登录业务。
 *
 * v1.0.0 是写死 macong/123456 + 随机 UUID，服务端既不存也不验；
 * 现在改成查 sys_user + BCrypt 校验 + 令牌落库，并把当前用户的角色信息一并返回。
 *
 * @version: 1.1
 * @date: 2026年11月05日, 00:20:00
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final SysUserMapper userMapper;

    /** 查角色 / 组装 CurrentUserVO 的公共逻辑，避免和拦截器写两遍 */
    private final RbacQueryService rbacQueryService;

    /** BCrypt 自带随机盐，同一明文每次编码结果都不同，所以只能用 matches() 比对，不能拿密文比字符串 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 令牌有效时长走配置，改时长不用重新打包。
     * ⚠️ key 必须和 application.yml 里的 cyxy.auth.token-hours 一模一样：
     *    @Value 是精确匹配，不做 Spring Boot 的松散绑定，写成 token_hours 会静默走默认值 8。
     */
    @Value("${cyxy.auth.token-hours:8}")
    private long tokenHours;

    public LoginVO login(LoginDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.username()) || !StringUtils.hasText(dto.password())) {
            throw new CYXYException(400, "用户名和密码不能为空");
        }

        // 按账号查，全局软删自动过滤 is_deleted=1。
        // 「账号不存在」「被禁用」「密码错误」统一返回同一句提示，避免暴露哪些账号存在。
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.username().trim()));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new CYXYException(400, "用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");

        // 只更新 3 个字段：造一个「补丁对象」交给 updateById。
        // MyBatis-Plus 默认只更新非 null 字段，不会把 passwordHash 等覆盖成空。
        // ⚠️ 反过来，如果哪天要「改密码后清空 token」，就不能用 updateById 传 null，
        //    得用 LambdaUpdateWrapper.set(SysUser::getToken, null)，见 AuthService。
        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setToken(token);
        patch.setTokenExpireTime(LocalDateTime.now().plusHours(tokenHours));
        patch.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(patch);

        // 登录覆盖旧 token = 「异地登录顶掉前一处」。3 个人用是优点：
        // 不会有人拿着旧链接一直能进；将来要支持多端同时在线，再拆令牌表。

        // ⚠️ /api/login 在拦截器白名单里，此刻 UserContext 是空的，
        //    所以角色必须自己查一次，不能写「从 UserContext 取」——那样拿到的永远是 null，
        //    前端就永远看不到「系统管理」入口。
        SysRole role = rbacQueryService.findRoleByUserId(user.getId());
        CurrentUserVO currentUser = rbacQueryService.toCurrentUserVO(user, role);

        // 用户名从 user 字段返回（不再是 userInfo），前端 login.vue 同步改了
        return new LoginVO(token, currentUser);
    }
}
