package com.cyxy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.common.AuthUser;
import com.cyxy.common.ResultCode;
import com.cyxy.common.UserContext;
import com.cyxy.mapper.SysMenuMapper;
import com.cyxy.mapper.SysRoleMenuMapper;
import com.cyxy.mapper.SysUserMapper;
import com.cyxy.model.entity.SysMenu;
import com.cyxy.model.entity.SysRole;
import com.cyxy.model.entity.SysRoleMenu;
import com.cyxy.model.entity.SysUser;
import com.cyxy.service.RbacQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 登录鉴权 + 菜单级读写权限。判定顺序见 preHandle 里的编号。
 *
 * 三个设计取舍：
 * 1) 令牌存库（sys_user.token），不用 JWT —— 3 个人、单机无 Redis，查一次库代价可忽略，
 *    换来「禁用账号 / 改角色 / 踢下线」立刻生效，也不用管密钥和续期。
 * 2) 只拦「明确登记为写接口」的路径，不按 HTTP 方法猜。
 *    /api/profit/calc 是 POST 但只算不写库，必须放行；该拦的是 /api/profit/save。
 * 3) 内置超管全放行且不查 sys_role_menu，保证权限表被清空也进得来。
 *
 * @version: 1.1
 * @date: 2026年11月05日, 00:20:00
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final RbacQueryService rbacQueryService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {

        // 【0】进来的第一件事先清上下文，而不是只依赖 afterCompletion。
        // 为什么：Tomcat 线程是复用的。如果上一次请求在 UserContext.set() 之后
        // 抛了异常（DB 超时等），它自己的 afterCompletion 不会执行，脏数据会留到这次。
        // 开头清一次，比依赖任何清理路径都可靠。
        UserContext.clear();

        // 【0.1】跨域预检请求不会带自定义头，也就没有 Authorization。
        // 现在前后端同源（Vite 代理 / nginx 反代）用不到，但一旦加了跨域必须放行，
        // 否则浏览器预检直接 401，真正的业务请求根本发不出去。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        String token = request.getHeader("Authorization");

        // 【1】认人：token 要同时满足「一致 + 未过期 + 账号启用」。
        // is_deleted=0 由全局软删配置自动拼上，不用手写。
        // 前端 request.js 把 cookie 里的 token 原样塞进 Authorization（没有 Bearer 前缀），
        // 所以这里直接拿去和 sys_user.token 比对，别自作主张去掉前缀。
        // 用 selectOne(wrapper) 而非 selectById —— 项目踩过 MyBatis 一级缓存
        // 返回同一实例的坑，wrapper 查询不会命中那个缓存。
        SysUser user = StringUtils.hasText(token) ? userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getToken, token)
                        .gt(SysUser::getTokenExpireTime, LocalDateTime.now())
                        .eq(SysUser::getStatus, 1)
        ) : null;
        if (user == null) {
            return deny(response, ResultCode.NOT_LOGIN.getCode(), "未登录或登录已过期");
        }

        // 【2】查角色。逻辑抽到 RbacQueryService，登录 / 当前用户 / 这里三处共用。
        // 查不到就当「未分配角色」：仍需登录，但业务接口全部挡住（见【4.1】）。
        SysRole role = rbacQueryService.findRoleByUserId(user.getId());
        AuthUser authUser = new AuthUser(user, role);
        UserContext.set(authUser);      // 后面的 Controller / Service 从这里取当前用户

        // 【3】内置超管不参与菜单勾选，永远全放行。必须放在所有限制之前。
        if (authUser.isSuperAdmin()) {
            return true;
        }

        // 【4】/api/system/** 是用户/角色/菜单的增删改，只允许超管进。
        if (path.startsWith("/api/system/")) {
            return deny(response, ResultCode.NO_PERMISSION.getCode(), "无权限");
        }

        // 【4.1】没分配角色（或角色被停用/删除）：只保留认证相关接口，
        // 让这个人还能查自己信息、改自己密码、正常登出，其余一律挡住。
        // 原实现在这里直接 return true，等于「没角色反而什么都能写」，是权限漏洞。
        if (role == null) {
            if (path.startsWith("/api/auth/")) {
                return true;
            }
            return deny(response, ResultCode.NO_PERMISSION.getCode(), "账号未分配角色，请联系管理员");
        }

        // 【5】菜单级只读：只有当前路径命中某菜单登记的写接口，才需要判断。
        // 每请求查一次带写接口清单的菜单，也就十来行；以后要优化可加缓存，一期先直查。
        List<SysMenu> menusWithWriteApi = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().isNotNull(SysMenu::getWriteApiPatterns));
        for (SysMenu menu : menusWithWriteApi) {
            // 空串也满足 isNotNull，跳过免得无谓 split
            if (!StringUtils.hasText(menu.getWriteApiPatterns())) {
                continue;
            }
            boolean hit = Arrays.stream(menu.getWriteApiPatterns().split(","))
                    .map(String::trim)
                    .anyMatch(path::equals);
            if (!hit) {
                continue;
            }

            // 命中写接口 → 查该角色对这个菜单是「可编辑」还是「只读」。
            // ⚠️ 原实现只在 rm != null && permType==0 时拦，等于「没勾这个菜单
            //    反而能写」——权限表里没有行，本来就是「看不到也用不了」，必须一起拦。
            SysRoleMenu rm = roleMenuMapper.selectOne(new LambdaQueryWrapper<SysRoleMenu>()
                    .eq(SysRoleMenu::getRoleId, role.getId())
                    .eq(SysRoleMenu::getMenuId, menu.getId()));
            if (rm == null || !Integer.valueOf(1).equals(rm.getPermType())) {
                return deny(response, ResultCode.NO_PERMISSION.getCode(), "该菜单未授权或为只读，无法修改数据");
            }
            break;      // 一个写接口只归属一个菜单，命中后不用再找
        }
        return true;
    }

    /**
     * 直接手写 JSON，不走 @RestControllerAdvice。
     * 为什么：GlobalExceptionHandler 的兜底会把异常包成 code:500 + HTTP 200，
     * 前端 request.js 认不出 401 就不会自动登出，403 也会显示成「服务器开小差」。
     * 这里沿用项目统一约定「HTTP 200 + 业务 code」，手写一段 Result 结构的 JSON。
     *
     * msg 都是代码里的固定常量、不含用户输入，所以字符串拼接没有注入风险；
     * 如果以后要回显路径之类，就换成 ObjectMapper 序列化 Result.fail(...)。
     *
     * 为什么这里还要 clear()：preHandle 返回 false 的拦截器不会触发自己的
     * afterCompletion，不主动清就会留下脏 ThreadLocal，下次请求可能串号。
     */
    private boolean deny(HttpServletResponse response, int code, String msg) throws IOException {
        UserContext.clear();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}");
        return false;
    }

    /** 请求结束后清线程变量，防线程池复用导致串号 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
