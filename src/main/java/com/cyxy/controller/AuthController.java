package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.model.dto.ChangePwdDTO;
import com.cyxy.model.vo.CurrentUserVO;
import com.cyxy.model.vo.MenuTreeNodeVO;
import com.cyxy.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证辅助接口，统一收在 /api/auth/**。
 *
 * 为什么不放在 /api/system/**：系统管理一级菜单只对超管可见，
 * 但「查自己信息 / 拿菜单 / 改自己密码 / 登出」是另外两个人也必须能做的事。
 * 拦截器对 /api/auth/** 只要求已登录（无角色用户也放行这几个），
 * 不要求超管 —— 这样没分配角色的人至少还能改密码、正常退出。
 *
 * 登录仍是 POST /api/login，是拦截器白名单里唯一的路径：
 * 保持线上 v1.0.0 前端不用改路径，也让白名单短到一眼能审完。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 刷新页面时用它覆盖 localStorage 里的旧用户信息 */
    @GetMapping("/current")
    public Result<CurrentUserVO> current() {
        return Result.ok(authService.current());
    }

    /** 登录时下发的菜单树（替掉原来的静态 menu.json） */
    @GetMapping("/menus")
    public Result<List<MenuTreeNodeVO>> menus() {
        return Result.ok(authService.menus());
    }

    /** 改自己密码：必须验旧密码，成功后所有会话失效，前端跳登录页重新登 */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePwdDTO dto) {
        authService.changePassword(dto);
        return Result.ok();
    }

    /** 登出：清库里的令牌；前端还要自己调 removeAllLoginInfo 清 cookie / localStorage */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }
}
