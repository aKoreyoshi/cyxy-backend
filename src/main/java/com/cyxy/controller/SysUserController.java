package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.exception.CYXYException;
import com.cyxy.model.dto.ResetPwdDTO;
import com.cyxy.model.dto.SysUserSaveDTO;
import com.cyxy.model.vo.SysUserVO;
import com.cyxy.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统管理 - 用户管理。
 * 整组 /api/system/user/** 由 AuthInterceptor 限定仅内置超管可进，
 * 这里不再重复做权限判断（只做业务上的自我保护，见 SysUserService）。
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /** 用户列表，keyword 模糊匹配账号或姓名；不传返回全部 */
    @GetMapping("/list")
    public Result<List<SysUserVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(sysUserService.list(keyword));
    }

    @PostMapping("/create")
    public Result<Void> create(@RequestBody SysUserSaveDTO dto) {
        sysUserService.create(dto);
        return Result.ok();
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestBody SysUserSaveDTO dto) {
        sysUserService.update(dto);
        return Result.ok();
    }

    /** 删除用 { "id": 1 } 请求体，和项目里产品删除接口保持同一种写法 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Object> body) {
        sysUserService.delete(toId(body));
        return Result.ok();
    }

    /** 超管重置密码：不需要旧密码，重置后对方被踢下线 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody ResetPwdDTO dto) {
        sysUserService.resetPassword(dto);
        return Result.ok();
    }

    /** 从请求体取 id，兼容前端传数字或字符串两种写法 */
    private Long toId(Map<String, Object> body) {
        Object id = body == null ? null : body.get("id");
        if (id == null) {
            throw new CYXYException("缺少用户 ID");
        }
        return Long.valueOf(id.toString());
    }
}
