package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.model.dto.LoginDTO;
import com.cyxy.service.LoginService;
import com.cyxy.model.vo.LoginVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: Koreyoshi
 * @description: 登录相关接口
 * @version: 1.0
 * @date: 2026年10月27日, 23:40:00
 */
@RestController
@RequestMapping("/api")
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        return Result.ok(loginService.login(dto));
    }
}
