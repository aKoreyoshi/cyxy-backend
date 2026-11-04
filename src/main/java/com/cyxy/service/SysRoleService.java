package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.SysRoleMapper;
import com.cyxy.mapper.SysRoleMenuMapper;
import com.cyxy.mapper.SysUserRoleMapper;
import com.cyxy.model.dto.MenuPermDTO;
import com.cyxy.model.dto.RoleSaveDTO;
import com.cyxy.model.entity.SysMenu;
import com.cyxy.model.entity.SysRole;
import com.cyxy.model.entity.SysRoleMenu;
import com.cyxy.model.vo.MenuPermItemVO;
import com.cyxy.model.vo.RoleDetailVO;
import com.cyxy.model.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统管理 - 角色管理。创建 / 改名 / 停用 / 删除，以及「按菜单逐个设可编辑或只读」。
 *
 * 内置超管角色（is_builtin=1）不参与菜单勾选、拦截器永远全放行，
 * 所以这里禁止对它做任何修改或删除 —— 否则权限表一乱就没人是管理员了。
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final RbacQueryService rbacQueryService;

    public List<RoleVO> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .orderByAsc(SysRole::getSortNo, SysRole::getId))
                .stream()
                .map(r -> new RoleVO(r.getId(), r.getRoleCode(), r.getRoleName(), r.getIsBuiltin(),
                        r.getSortNo(), r.getStatus(), r.getRemark()))
                .toList();
    }

    /**
     * 新建角色时的「空白菜单勾选项」。
     *
     * 为什么需要单独一个接口：/detail 必须传角色 id，可新建时角色还不存在，
     * 前端就没地方拿全量菜单和 hasWriteApi 标记（菜单树 VO 不带这个字段）。
     * 这里返回和 detail.menus 完全同构的数据，只是 permType 全为 null，
     * 前端建树逻辑两处可以复用，不用写两套。
     *
     * 同样过滤掉 SYSTEM_ 系统管理组：普通角色勾了后端也不会下发，
     * 干脆不展示，免得管理员以为配上了其实没生效。
     */
    public List<MenuPermItemVO> menuOptions() {
        return rbacQueryService.allEnabledMenus().stream()
                .filter(m -> !RbacQueryService.isSystemMenu(m))
                .map(m -> new MenuPermItemVO(m.getId(), m.getParentId(), m.getTitle(),
                        m.getMenuType(), m.getSortNo(), null,
                        StringUtils.hasText(m.getWriteApiPatterns())))
                .toList();
    }

    /**
     * 角色详情 = 基本信息 + 全量启用菜单的勾选情况。
     * 没勾的菜单 permType 为 null；hasWriteApi 让前端把「无写操作」的只读开关置灰。
     */
    public RoleDetailVO detail(Long id) {
        SysRole role = requireRole(id);
        List<SysMenu> allMenus = rbacQueryService.allEnabledMenus();
        // 普通角色的勾选树里不出现系统管理菜单：勾了后端也不会下发，
        // 干脆不展示，免得管理员以为配上了其实没生效。
        if (!Integer.valueOf(1).equals(role.getIsBuiltin())) {
            allMenus = allMenus.stream().filter(m -> !RbacQueryService.isSystemMenu(m)).toList();
        }

        Map<Long, Integer> granted = new HashMap<>();
        if (Integer.valueOf(1).equals(role.getIsBuiltin())) {
            // 内置超管表里没有授权行，但界面上要显示成「全部可编辑」（且整页置灰）
            for (SysMenu menu : allMenus) {
                granted.put(menu.getId(), 1);
            }
        } else {
            granted.putAll(roleMenuMapper.selectList(
                            new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id))
                    .stream()
                    .collect(Collectors.toMap(SysRoleMenu::getMenuId, SysRoleMenu::getPermType, (a, b) -> a)));
        }

        List<MenuPermItemVO> items = allMenus.stream()
                .map(m -> new MenuPermItemVO(m.getId(), m.getParentId(), m.getTitle(),
                        m.getMenuType(), m.getSortNo(), granted.get(m.getId()),
                        StringUtils.hasText(m.getWriteApiPatterns())))
                .toList();
        return new RoleDetailVO(role.getId(), role.getRoleCode(), role.getRoleName(),
                role.getIsBuiltin(), role.getSortNo(), role.getStatus(), role.getRemark(), items);
    }

    /**
     * 新建角色。基本信息 + 菜单权限在同一个事务里写，
     * 不会出现「角色建了但权限没存上」的半截状态。
     */
    @Transactional
    public void create(RoleSaveDTO dto) {
        String code = dto.roleCode() == null ? "" : dto.roleCode().trim();
        if (!StringUtils.hasText(code) || !StringUtils.hasText(dto.roleName())) {
            throw new CYXYException(400, "角色编码和名称不能为空");
        }

        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(dto.roleName());
        role.setSortNo(dto.sortNo() == null ? 0 : dto.sortNo());
        role.setStatus(dto.status() == null ? 1 : dto.status());
        role.setRemark(dto.remark());
        // ⚠️ 新建角色一律 isBuiltin=0。内置超管是唯一「永远全放行」的角色，
        //    绝不能从页面上再造一个，否则拦截器的超管短路判断会被滥用。
        role.setIsBuiltin(0);

        // 同用户表：唯一索引只含 role_code，软删行仍占位，靠索引兜底给友好提示
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException e) {
            throw new CYXYException(400, "角色编码已存在（含已删除角色，请换一个）");
        }
        replaceMenus(role.getId(), dto.menus());
    }

    @Transactional
    public void update(RoleSaveDTO dto) {
        if (dto.id() == null) {
            throw new CYXYException(400, "缺少角色 ID");
        }
        SysRole existing = requireRole(dto.id());
        if (Integer.valueOf(1).equals(existing.getIsBuiltin())) {
            throw new CYXYException(400, "内置超管角色不可修改");
        }

        // 只改名称 / 排序 / 状态 / 备注。roleCode 不开放修改：
        // 它是业务代码里的稳定标识，改了容易和外部配置对不上。
        SysRole patch = new SysRole();
        patch.setId(dto.id());
        patch.setRoleName(dto.roleName());
        patch.setSortNo(dto.sortNo());
        patch.setStatus(dto.status());
        patch.setRemark(dto.remark());
        roleMapper.updateById(patch);
        replaceMenus(dto.id(), dto.menus());
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (Integer.valueOf(1).equals(role.getIsBuiltin())) {
            throw new CYXYException(400, "内置超管角色不可删除");
        }
        Long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<com.cyxy.model.entity.SysUserRole>()
                        .eq(com.cyxy.model.entity.SysUserRole::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new CYXYException(400, "还有 " + userCount + " 个用户使用该角色，请先改派");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        roleMapper.deleteById(id);      // 逻辑删除
    }

    private SysRole requireRole(Long id) {
        if (id == null) {
            throw new CYXYException(400, "缺少角色 ID");
        }
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getId, id));
        if (role == null) {
            throw new CYXYException(400, "角色不存在");
        }
        return role;
    }

    /**
     * 保存菜单勾选：先删后插，必须包在调用方的事务里。
     *
     * 关联表没有 is_deleted，所以是物理删；如果不用事务，删完插到一半失败
     * 就会留下一个「什么菜单都看不到」的角色。
     *
     * 另外过滤掉不存在 / 已停用的 menuId，避免前端传脏数据把权限表塞满垃圾行。
     */
    private void replaceMenus(Long roleId, List<MenuPermDTO> menus) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menus == null || menus.isEmpty()) {
            return;     // 允许一个菜单都不勾（先把角色建出来，稍后再配）
        }
        Set<Long> validIds = rbacQueryService.allEnabledMenus().stream()
                .map(SysMenu::getId)
                .collect(Collectors.toSet());
        for (MenuPermDTO item : menus) {
            if (item == null || item.menuId() == null || !validIds.contains(item.menuId())) {
                continue;
            }
            SysRoleMenu row = new SysRoleMenu();
            row.setRoleId(roleId);
            row.setMenuId(item.menuId());
            // 只有明确传 1 才算可编辑，其余（含 null / 0）一律按只读存，保守优先
            row.setPermType(Integer.valueOf(1).equals(item.permType()) ? 1 : 0);
            roleMenuMapper.insert(row);
        }
    }
}
