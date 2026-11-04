package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.exception.CYXYException;
import com.cyxy.model.dto.RoleSaveDTO;
import com.cyxy.model.vo.MenuPermItemVO;
import com.cyxy.model.vo.RoleDetailVO;
import com.cyxy.model.vo.RoleVO;
import com.cyxy.service.SysRoleService;
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
 * 系统管理 - 角色管理。
 * 创建 / 修改时把菜单勾选（含每个菜单的可编辑或只读）一起提交，一次事务写完。
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping("/list")
    public Result<List<RoleVO>> list() {
        return Result.ok(sysRoleService.list());
    }

    /** 角色详情：基本信息 + 全量菜单勾选情况，给角色编辑弹窗用 */
    @GetMapping("/detail")
    public Result<RoleDetailVO> detail(@RequestParam Long id) {
        return Result.ok(sysRoleService.detail(id));
    }

    /**
     * 新建角色时的空白菜单勾选项。
     * /detail 需要已存在的角色 id，新建时拿不到，所以单独给一份同构数据；
     * 前端新建调这个、编辑调 /detail，建树逻辑可以完全复用。
     */
    @GetMapping("/menu-options")
    public Result<List<MenuPermItemVO>> menuOptions() {
        return Result.ok(sysRoleService.menuOptions());
    }

    @PostMapping("/create")
    public Result<Void> create(@RequestBody RoleSaveDTO dto) {
        sysRoleService.create(dto);
        return Result.ok();
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestBody RoleSaveDTO dto) {
        sysRoleService.update(dto);
        return Result.ok();
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Object> body) {
        sysRoleService.delete(toId(body));
        return Result.ok();
    }

    /** 从请求体取 id，兼容数字与字符串 */
    private Long toId(Map<String, Object> body) {
        Object id = body == null ? null : body.get("id");
        if (id == null) {
            throw new CYXYException("缺少角色 ID");
        }
        return Long.valueOf(id.toString());
    }
}
