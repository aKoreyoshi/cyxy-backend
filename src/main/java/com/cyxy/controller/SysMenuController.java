package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.exception.CYXYException;
import com.cyxy.model.dto.MenuSaveDTO;
import com.cyxy.model.vo.MenuTreeNodeVO;
import com.cyxy.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统管理 - 菜单管理。
 * 一期只允许改名 / 排序 / 启停 / 换上级 / 新建分组目录，
 * 具体约束在 MenuSaveDTO 和 SysMenuService 里，Controller 只做转发。
 */
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /** 全量菜单树（超管视图），给菜单管理页展示层级 */
    @GetMapping("/tree")
    public Result<List<MenuTreeNodeVO>> tree() {
        return Result.ok(sysMenuService.tree());
    }

    @PostMapping("/create")
    public Result<Void> create(@RequestBody MenuSaveDTO dto) {
        sysMenuService.create(dto);
        return Result.ok();
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestBody MenuSaveDTO dto) {
        sysMenuService.update(dto);
        return Result.ok();
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Object> body) {
        sysMenuService.delete(toId(body));
        return Result.ok();
    }

    /** 从请求体取 id，兼容数字与字符串 */
    private Long toId(Map<String, Object> body) {
        Object id = body == null ? null : body.get("id");
        if (id == null) {
            throw new CYXYException("缺少菜单 ID");
        }
        return Long.valueOf(id.toString());
    }
}
