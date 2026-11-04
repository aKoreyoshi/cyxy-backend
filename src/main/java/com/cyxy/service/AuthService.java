package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cyxy.common.AuthUser;
import com.cyxy.common.UserContext;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.SysUserMapper;
import com.cyxy.model.dto.ChangePwdDTO;
import com.cyxy.model.entity.SysUser;
import com.cyxy.model.vo.CurrentUserVO;
import com.cyxy.model.vo.MenuTreeNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 认证辅助接口：当前用户、菜单树、改自己密码、登出。
 *
 * 这几个接口对**所有已登录用户**开放（不要求超管），所以它们放在 /api/auth/**
 * 而不是 /api/system/** —— 系统管理菜单只对超管可见，另外两个人得能改自己密码。
 * 拦截器会先验证登录，这里再从 UserContext 取当前用户，不用再查一次 token。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final RbacQueryService rbacQueryService;

    /** 和 LoginService 各持一个无状态实例，功能上无影响；后续可抽成 @Bean 共用 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 取当前请求的登录上下文。
     * 正常路径下拦截器一定 set 过；这里再判一次是防止有人绕过 WebMvc 直接调 Service。
     */
    private AuthUser requireAuth() {
        AuthUser ctx = UserContext.get();
        if (ctx == null) {
            throw new CYXYException(401, "未登录或登录已过期");
        }
        return ctx;
    }

    /** 刷新页面时用：前端拿它覆盖 localStorage 里的旧用户信息 */
    public CurrentUserVO current() {
        AuthUser ctx = requireAuth();
        return rbacQueryService.toCurrentUserVO(ctx.user(), ctx.role());
    }

    /**
     * 当前用户的菜单树。超管全量，普通角色只给勾过的（并自动补祖先和隐藏页）。
     * 前端登录时拿到这份树存进 store，替掉原来的静态 menu.json。
     */
    public List<MenuTreeNodeVO> menus() {
        AuthUser ctx = requireAuth();
        return rbacQueryService.buildMenuTree(ctx.role(), ctx.isSuperAdmin());
    }

    /**
     * 修改自己的密码。
     *
     * 为什么必须验旧密码：这个接口对所有登录用户开放，不验的话，
     * 别人借你没锁屏的浏览器就能直接把密码改掉。
     *
     * 改完把 token 和过期时间一起清空 —— 该用户所有已登录会话立刻失效，
     * 前端收到成功后跳登录页重新登一次即可。这也符合「改密码要踢下线」的常识。
     */
    @Transactional
    public void changePassword(ChangePwdDTO dto) {
        AuthUser ctx = requireAuth();
        if (dto == null || !StringUtils.hasText(dto.newPwd()) || dto.newPwd().length() < 6) {
            throw new CYXYException(400, "新密码不能为空且至少 6 位");
        }
        if (!StringUtils.hasText(dto.oldPwd())
                || !passwordEncoder.matches(dto.oldPwd(), ctx.user().getPasswordHash())) {
            throw new CYXYException(400, "原密码不正确");
        }

        // ⚠️ 这里不能用 updateById(patch)：MyBatis-Plus 默认只更新非 null 字段，
        //    想把 token「清成 null」它根本不会拼进 SQL，旧令牌会继续有效。
        //    必须走 LambdaUpdateWrapper.set(..., null) 显式赋值。
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, ctx.user().getId())
                .set(SysUser::getPasswordHash, passwordEncoder.encode(dto.newPwd()))
                .set(SysUser::getToken, null)
                .set(SysUser::getTokenExpireTime, null));
    }

    /** 登出：只清库里的令牌，前端还要自己清 cookie / localStorage（removeAllLoginInfo） */
    @Transactional
    public void logout() {
        AuthUser ctx = UserContext.get();
        if (ctx == null) {
            return;     // 拦截器保证正常路径不会走到这里，留个保险
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, ctx.user().getId())
                .set(SysUser::getToken, null)
                .set(SysUser::getTokenExpireTime, null));
    }
}
