package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.SysMenuMapper;
import com.cyxy.mapper.SysRoleMenuMapper;
import com.cyxy.model.dto.MenuSaveDTO;
import com.cyxy.model.entity.SysMenu;
import com.cyxy.model.entity.SysRoleMenu;
import com.cyxy.model.vo.MenuTreeNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统管理 - 菜单管理。
 *
 * 一期只开放：改名、排序、启停、换上级、新建「分组目录」。
 * 不开放新建可点击页面，也不允许改 url / routeName / writeApiPatterns：
 * 前端路由是代码里注册好的，凭空造 url 点进去会白屏；
 * 写接口清单更是权限拦截的依据，能被页面改就等于能绕过只读控制。
 * 这两点靠 MenuSaveDTO 里「根本没有这些字段」从类型上保证。
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final RbacQueryService rbacQueryService;

    /**
     * 菜单管理页用全量树（含停用菜单）。
     *
     * ⚠️ 不能用 buildMenuTree(null, true)：那个方法只查 visible=1 的菜单，
     *    一旦管理员把某菜单停用，它就会从管理页彻底消失、再也启用不回来。
     *    管理页必须看到全量，登录时下发的导航树才按 visible 过滤，两者用途不同。
     */
    public List<MenuTreeNodeVO> tree() {
        return rbacQueryService.buildFullMenuTree();
    }

    @Transactional
    public void create(MenuSaveDTO dto) {
        if (!StringUtils.hasText(dto.title())) {
            throw new CYXYException(400, "菜单名称不能为空");
        }
        Long parentId = dto.parentId() == null ? 0L : dto.parentId();
        if (parentId != 0L) {
            SysMenu parent = menuMapper.selectOne(
                    new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getId, parentId));
            if (parent == null) {
                throw new CYXYException(400, "上级菜单不存在");
            }
        }

        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setTitle(dto.title().trim());
        menu.setIcon(dto.icon());
        menu.setSortNo(dto.sortNo() == null ? 0 : dto.sortNo());
        menu.setVisible(dto.visible() == null ? 1 : dto.visible());
        menu.setMenuType(0);       // 0 = 显示在侧边栏，沿用前端 menuConfig.menuValue
        menu.setIsBuiltin(0);      // 新建的一律非内置，允许以后删除
        // url / routeName / writeApiPatterns 全部留 null：
        // 一期新建的只能是分组目录，不指向任何页面，也不承载写接口
        menuMapper.insert(menu);
    }

    @Transactional
    public void update(MenuSaveDTO dto) {
        if (dto.id() == null) {
            throw new CYXYException(400, "缺少菜单 ID");
        }
        SysMenu existing = menuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getId, dto.id()));
        if (existing == null) {
            throw new CYXYException(400, "菜单不存在");
        }
        if (dto.parentId() != null && isSelfOrDescendant(dto.parentId(), dto.id())) {
            throw new CYXYException(400, "不能把菜单挂到自己或自己的子菜单下");
        }

        SysMenu patch = new SysMenu();
        patch.setId(dto.id());
        patch.setParentId(dto.parentId());
        patch.setTitle(dto.title());
        patch.setIcon(dto.icon());
        patch.setSortNo(dto.sortNo());
        patch.setVisible(dto.visible());
        // updateById 只更新非 null 字段，url / menuType / routeName /
        // writeApiPatterns / isBuiltin 原样保留 —— 这就是「内置菜单只能改显示属性」的实现
        menuMapper.updateById(patch);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new CYXYException(400, "缺少菜单 ID");
        }
        SysMenu existing = menuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getId, id));
        if (existing == null) {
            throw new CYXYException(400, "菜单不存在");
        }
        if (Integer.valueOf(1).equals(existing.getIsBuiltin())) {
            throw new CYXYException(400, "内置菜单不可删除，如需移除请改用「停用」");
        }
        Long childCount = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new CYXYException(400, "请先删除或移走下级菜单");
        }

        // 清掉各角色对该菜单的授权行，避免留下指向已删菜单的垃圾数据
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        menuMapper.deleteById(id);      // 逻辑删除
    }

    /**
     * 判断 candidateParentId 是不是 menuId 自己、或它的某个子孙。
     * 为什么需要：允许把父菜单挂到子菜单下会形成环，
     * 前端 arr2tree 建树时会无限递归或直接把整棵菜单弄丢。
     * 从 candidateParentId 沿 parentId 往上走，能碰到 menuId 就说明会成环。
     */
    private boolean isSelfOrDescendant(Long candidateParentId, Long menuId) {
        if (Objects.equals(candidateParentId, menuId)) {
            return true;
        }
        Map<Long, SysMenu> byId = menuMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysMenu::getId, Function.identity(), (a, b) -> a));
        Long cursor = candidateParentId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 20) {
            if (Objects.equals(cursor, menuId)) {
                return true;
            }
            SysMenu node = byId.get(cursor);
            cursor = node == null ? null : node.getParentId();
        }
        return false;
    }
}
