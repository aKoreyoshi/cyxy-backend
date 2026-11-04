package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.mapper.SysMenuMapper;
import com.cyxy.mapper.SysRoleMapper;
import com.cyxy.mapper.SysRoleMenuMapper;
import com.cyxy.mapper.SysUserRoleMapper;
import com.cyxy.model.entity.SysMenu;
import com.cyxy.model.entity.SysRole;
import com.cyxy.model.entity.SysRoleMenu;
import com.cyxy.model.entity.SysUser;
import com.cyxy.model.entity.SysUserRole;
import com.cyxy.model.vo.CurrentUserVO;
import com.cyxy.model.vo.MenuTreeNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RBAC 公共查询：查角色、组装当前用户、构建下发给前端的菜单树。
 *
 * 为什么单独抽一个 Service：登录（/api/login 在白名单、UserContext 是空的）、
 * 鉴权拦截器、/api/auth/current 三处都要「按 userId 查角色」和「组装用户 VO」。
 * 抽出来避免同一个 last("limit 1") 逻辑散落在三个地方，以后改多角色只改这里。
 *
 * 注意：这里只做查询，不做任何写操作，所以不需要 @Transactional。
 */
@Service
@RequiredArgsConstructor
public class RbacQueryService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    /**
     * 查用户角色。界面上一个用户只选一个角色，所以取第一条就够。
     * 表虽然按多对多建（联合主键），但一期不做多角色合并，避免权限叠加逻辑复杂化。
     *
     * 停用（status=0）或被软删的角色当成「没有角色」返回 null —— 这样拦截器会走
     * 「未分配角色」分支把业务接口全部挡住，而不是留着一个失效角色继续放行。
     */
    public SysRole findRoleByUserId(Long userId) {
        SysUserRole relation = userRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .last("limit 1"));
        if (relation == null) {
            return null;
        }
        // is_deleted=0 由全局软删配置自动拼上；这里额外挡掉 status=0 的停用角色
        return roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getId, relation.getRoleId())
                .eq(SysRole::getStatus, 1));
    }

    /**
     * 组装「当前登录用户」。
     * 只挑前端用得到的字段，绝不把 passwordHash / token 塞进去 ——
     * 直接序列化 SysUser 等于把密码密文和有效令牌一起交给浏览器。
     *
     * superAdmin 只用于前端决定「系统管理」入口显不显示，不是权限依据：
     * localStorage 用户自己能改，真正的判断每次请求都在 AuthInterceptor 里重新查库。
     */
    public CurrentUserVO toCurrentUserVO(SysUser user, SysRole role) {
        boolean superAdmin = role != null && Integer.valueOf(1).equals(role.getIsBuiltin());
        return new CurrentUserVO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                role == null ? null : role.getRoleCode(),
                role == null ? null : role.getRoleName(),
                superAdmin);
    }

    /** 启用中的全部菜单，按 sort_no 升序。is_deleted=0 由全局软删自动拼上。 */
    public List<SysMenu> allEnabledMenus() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getVisible, 1)
                .orderByAsc(SysMenu::getSortNo, SysMenu::getId));
    }

    /**
     * 判断是不是「系统管理」组的菜单。初始化脚本给这一组菜单的
     * route_name 统一加 SYSTEM_ 前缀，就是为了这里能稳定识别。
     *
     * 为什么用 route_name 而不是标题或 url：标题管理员可以随便改，
     * 一级目录的 url 又是空的，只有 route_name 是后台稳定标识。
     */
    public static boolean isSystemMenu(SysMenu menu) {
        return menu != null && menu.getRouteName() != null
                && menu.getRouteName().startsWith("SYSTEM_");
    }

    /**
     * 构造下发给前端的菜单树。三个容易踩坑的地方：
     *
     * 1) 内置超管不查 sys_role_menu，直接全给、permType 全 1。
     *    这样哪怕权限表被清空，超管也进得来，不会把自己锁在外面。
     *
     * 2) 普通角色只给勾过的菜单，但必须「补齐祖先目录」。
     *    前端 arr2tree 靠 parentId 建树，只给子菜单不给父目录，层级会整个乱掉。
     *
     * 3) menuType=1 的隐藏功能页（如 /product/detail）要跟着父菜单一起下发。
     *    router/index.js 的路由守卫用「完整菜单数组」判断 to.path 是否合法，
     *    漏发隐藏页会让产品详情页直接跳 404。隐藏页权限继承父菜单，不要求单独勾。
     */
    public List<MenuTreeNodeVO> buildMenuTree(SysRole role, boolean superAdmin) {
        List<SysMenu> all = allEnabledMenus();
        // 系统管理组只对内置超管可见。即使超管在角色管理页手滑勾给普通角色，
        // 后端也不下发，保证这条规则不依赖前端自觉、也不依赖配置不出错。
        if (!superAdmin) {
            all = all.stream().filter(m -> !isSystemMenu(m)).toList();
        }
        Map<Long, SysMenu> menuById = all.stream()
                .collect(Collectors.toMap(SysMenu::getId, m -> m, (a, b) -> a));

        // menuId -> permType（1 可编辑 / 0 只读）；不在 map 里 = 不下发
        Map<Long, Integer> granted = new HashMap<>();

        if (superAdmin) {
            for (SysMenu menu : all) {
                granted.put(menu.getId(), 1);
            }
        } else if (role != null) {
            List<SysRoleMenu> rows = roleMenuMapper.selectList(
                    new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getId()));
            for (SysRoleMenu row : rows) {
                SysMenu menu = menuById.get(row.getMenuId());
                if (menu == null) {
                    continue;   // 菜单已停用或被删，忽略这条残留授权
                }
                granted.put(menu.getId(), row.getPermType());

                // 沿 parentId 一路补祖先。guard 防止脏数据形成环导致死循环。
                Long parentId = menu.getParentId();
                int guard = 0;
                while (parentId != null && parentId != 0L && guard++ < 10) {
                    if (granted.containsKey(parentId) || !menuById.containsKey(parentId)) {
                        break;
                    }
                    granted.put(parentId, 1);   // 目录本身没有写接口，给可编辑即可
                    parentId = menuById.get(parentId).getParentId();
                }
            }

            // 隐藏功能页跟随父菜单权限。父菜单没勾就一起不下发。
            for (SysMenu menu : all) {
                if (Integer.valueOf(1).equals(menu.getMenuType()) && !granted.containsKey(menu.getId())) {
                    Integer parentPerm = granted.get(menu.getParentId());
                    if (parentPerm != null) {
                        granted.put(menu.getId(), parentPerm);
                    }
                }
            }
        }

        return assembleTree(all, granted);
    }

    /**
     * 菜单管理页专用：返回**全量**菜单树（含停用、含系统管理组），不做任何权限过滤。
     *
     * 为什么不能复用 buildMenuTree(superAdmin=true)：那个方法的数据源是
     * allEnabledMenus()，只查 visible=1 的菜单。菜单一旦在页面上被停用，
     * 管理页就再也看不到它、也就没法重新启用，等于「停用 = 永久消失」。
     * 这里直接查全表；granted 全给 1 只是让 assembleTree 把每个节点都生成出来，
     * 跟权限判定没有任何关系。
     */
    public List<MenuTreeNodeVO> buildFullMenuTree() {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortNo, SysMenu::getId));
        Map<Long, Integer> granted = new HashMap<>();
        for (SysMenu menu : all) {
            granted.put(menu.getId(), 1);
        }
        return assembleTree(all, granted);
    }

    /**
     * 把「菜单列表 + 哪些 id 要下发」组装成树。
     * 抽出来是因为登录菜单树和菜单管理树只差数据源与 granted，建树过程完全一样。
     */
    private List<MenuTreeNodeVO> assembleTree(List<SysMenu> all, Map<Long, Integer> granted) {
        if (granted.isEmpty()) {
            return List.of();   // 无角色 / 没勾任何菜单：返回空树，前端会停在 404 页
        }

        // 先按 granted 生成所有节点（children 用可变的 ArrayList，后面才能挂子节点），
        // 再遍历一次把子节点挂到父节点上；挂不上的（一级菜单或父被过滤）当成根。
        Map<Long, MenuTreeNodeVO> nodes = new LinkedHashMap<>();
        for (SysMenu menu : all) {
            Integer perm = granted.get(menu.getId());
            if (perm == null) {
                continue;
            }
            nodes.put(menu.getId(), new MenuTreeNodeVO(
                    menu.getId(), menu.getParentId(), menu.getTitle(), menu.getUrl(), menu.getIcon(),
                    menu.getMenuType(), menu.getSortNo(), menu.getVisible(), perm,
                    menu.getIsBuiltin(), menu.getRouteName(), new ArrayList<>()));
        }

        List<MenuTreeNodeVO> roots = new ArrayList<>();
        for (MenuTreeNodeVO node : nodes.values()) {
            MenuTreeNodeVO parent = nodes.get(node.parentId());
            if (parent == null || Objects.equals(parent.id(), node.id())) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }

        Comparator<MenuTreeNodeVO> bySort = Comparator
                .comparing((MenuTreeNodeVO n) -> n.sortNo() == null ? 0 : n.sortNo())
                .thenComparing(n -> n.id() == null ? 0L : n.id());
        sortRecursively(roots, bySort);
        return roots;
    }

    /** 递归排序：保证「首页」排第一，前端 router/index.js 会跳第一个有 url 的菜单 */
    private void sortRecursively(List<MenuTreeNodeVO> list, Comparator<MenuTreeNodeVO> comparator) {
        list.sort(comparator);
        for (MenuTreeNodeVO node : list) {
            sortRecursively(node.children(), comparator);
        }
    }
}
