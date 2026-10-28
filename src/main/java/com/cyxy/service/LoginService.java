package com.cyxy.service;

import com.cyxy.model.dto.LoginDTO;
import com.cyxy.exception.CYXYException;
import com.cyxy.model.vo.LoginVO;
import com.cyxy.model.vo.UserInfoVO;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author: Koreyoshi
 * @description: 登录业务（当前账号写死，接入数据库后改为查表校验）
 * @version: 1.0
 * @date: 2026年10月27日, 23:40:00
 */
@Service
public class LoginService {

    /** 临时账号：接入数据库后由用户表替代 */
    private static final String MOCK_USERNAME = "macong";
    private static final String MOCK_PASSWORD = "123456";

    public LoginVO login(LoginDTO dto) {
        if (dto == null || dto.username() == null || dto.password() == null) {
            throw new CYXYException(400, "用户名和密码不能为空");
        }
        if (!MOCK_USERNAME.equals(dto.username().trim()) || !MOCK_PASSWORD.equals(dto.password())) {
            throw new CYXYException(400, "用户名或密码错误");
        }

        // 目前 token 仅用于打通流程，后续可替换为 JWT
        String token = UUID.randomUUID().toString().replace("-", "");
        UserInfoVO userInfo = new UserInfoVO(1L, MOCK_USERNAME, "管理员");
        return new LoginVO(token, userInfo);
    }
}
