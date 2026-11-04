package com.cyxy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cyxy.common.AuthUser;
import com.cyxy.common.UserContext;
import com.cyxy.config.AuthInterceptor;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.SysMenuMapper;
import com.cyxy.mapper.SysRoleMenuMapper;
import com.cyxy.mapper.SysRoleMapper;
import com.cyxy.mapper.SysUserMapper;
import com.cyxy.mapper.SysUserRoleMapper;
import com.cyxy.model.dto.ChangePwdDTO;
import com.cyxy.model.entity.SysMenu;
import com.cyxy.model.entity.SysRole;
import com.cyxy.model.entity.SysRoleMenu;
import com.cyxy.model.entity.SysUser;
import com.cyxy.model.entity.SysUserRole;
import com.cyxy.model.vo.MenuTreeNodeVO;
import com.cyxy.service.AuthService;
import com.cyxy.service.RbacQueryService;
import com.cyxy.service.SysRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限控制一期回归测试。
 *
 * 全程 @Transactional，测试结束自动回滚，不会污染本地库的初始数据。
 *
 * 为什么不用 MockMvc：Spring Boot 4 把 @AutoConfigureMockMvc 拆到了独立的
 * spring-boot-webmvc-test 模块，本地仓库没有、也不想为测试临时拉新依赖。
 * 这里直接拿 MockHttpServletRequest 调拦截器的 preHandle，反而更精准 ——
 * 本次要验的就是拦截器的判定顺序，不受 Controller 参数校验结果干扰。
 *
 * ⚠️ 断言一律用 route_name / url，不写死菜单的数字 id：
 *    菜单 id 会因为整理顺序而整体重排（见 2026-11-05_menu_reorder.sql），
 *    写死 id 会让测试在下次重排后集体误报「权限坏了」这种吓人的假警。
 */
@SpringBootTest
@Transactional
class RbacPermissionTest {

    @Autowired private AuthInterceptor authInterceptor;
    @Autowired private SysUserMapper userMapper;
    @Autowired private SysRoleMapper roleMapper;
    @Autowired private SysMenuMapper menuMapper;
    @Autowired private SysRoleMenuMapper roleMenuMapper;
    @Autowired private SysUserRoleMapper userRoleMapper;
    @Autowired private RbacQueryService rbacQueryService;
    @Autowired private SysRoleService sysRoleService;
    @Autowired private AuthService authService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 测试里没有 DispatcherServlet 帮我们调 afterCompletion，每个用例后手动清 */
    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    /** 一次拦截器判定的结果 */
    private record AuthResult(boolean passed, boolean superAdmin, String body) {}

    /**
     * 直接跑 preHandle。
     * 注意断言的是「HTTP 200 + 业务 code」：拦截器 401/403 是手写 JSON 返回的，
     * 不是真的 HTTP 状态码（前端 request.js 只认业务 code）。
     */
    private AuthResult call(String method, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (token != null) {
            request.addHeader("Authorization", token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean passed = authInterceptor.preHandle(request, response, new Object());
        AuthUser ctx = UserContext.get();
        return new AuthResult(passed, ctx != null && ctx.isSuperAdmin(),
                response.getContentAsString());
    }

    /** 初始化脚本里的内置超管账号 */
    private SysUser superAdmin() {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "macong"));
        assertNotNull(user, "初始化脚本没跑？macong 账号不存在");
        return user;
    }

    /**
     * 按稳定标识 route_name 查菜单 id。
     * 不写死数字：菜单整理过一次 id 就全变了，写死会让测试变成「重排报警器」。
     */
    private Long menuId(String routeName) {
        SysMenu menu = menuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getRouteName, routeName));
        assertNotNull(menu, "初始化脚本没跑？菜单 " + routeName + " 不存在");
        return menu.getId();
    }

    /** 给指定用户发一个有效令牌（直接写库，绕过登录接口），返回令牌串 */
    private String issueToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getToken, token)
                .set(SysUser::getTokenExpireTime, LocalDateTime.now().plusHours(1)));
        return token;
    }

    /** 造一个普通角色 + 用户，并按需给它挂菜单授权 */
    private SysUser createViewer(boolean grantProductList, int permType) {
        SysRole role = new SysRole();
        role.setRoleCode("TEST_VIEWER");
        role.setRoleName("测试只读角色");
        role.setIsBuiltin(0);
        role.setSortNo(99);
        role.setStatus(1);
        roleMapper.insert(role);

        SysUser user = new SysUser();
        user.setUsername("test_viewer");
        user.setPasswordHash(encoder.encode("123456"));
        user.setRealName("测试用户");
        user.setStatus(1);
        userMapper.insert(user);

        SysUserRole relation = new SysUserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(role.getId());
        userRoleMapper.insert(relation);

        if (grantProductList) {
            SysRoleMenu grant = new SysRoleMenu();
            grant.setRoleId(role.getId());
            grant.setMenuId(menuId("PRODUCT_LIST"));
            grant.setPermType(permType);
            roleMenuMapper.insert(grant);
        }
        return user;
    }

    private List<MenuTreeNodeVO> flatten(List<MenuTreeNodeVO> tree) {
        List<MenuTreeNodeVO> result = new ArrayList<>();
        for (MenuTreeNodeVO node : tree) {
            result.add(node);
            result.addAll(flatten(node.children()));
        }
        return result;
    }

    @Test
    @DisplayName("超管账号的密码是 BCrypt 且 123456 能匹配")
    void superAdminPasswordIsBcrypt() {
        SysUser admin = superAdmin();
        assertTrue(admin.getPasswordHash().startsWith("$2"));
        assertTrue(encoder.matches("123456", admin.getPasswordHash()));
        assertFalse(encoder.matches("wrong-password", admin.getPasswordHash()));
    }

    @Test
    @DisplayName("超管被识别为内置角色，菜单树全量、首页第一、隐藏页在内")
    void superAdminMenuTree() {
        SysUser admin = superAdmin();
        SysRole role = rbacQueryService.findRoleByUserId(admin.getId());
        assertNotNull(role);
        assertTrue(new AuthUser(admin, role).isSuperAdmin());

        List<MenuTreeNodeVO> tree = rbacQueryService.buildMenuTree(role, true);
        assertFalse(tree.isEmpty());
        assertEquals("首页", tree.get(0).title(), "首页必须排第一，否则路由守卫默认跳错页面");
        // 一级菜单顺序由 sort_no 决定，改动会直接影响侧边栏和登录默认页，
        // 所以在这里锁死，避免以后整理菜单时手滑改乱却没人发现。
        assertEquals(List.of("首页", "系统管理", "产品管理", "运营工具", "参数列表"),
                tree.stream().map(MenuTreeNodeVO::title).toList(),
                "一级菜单顺序应符合 2026-11-05_menu_reorder.sql 的约定");

        List<MenuTreeNodeVO> flat = flatten(tree);
        assertTrue(flat.stream().anyMatch(n -> "/product/detail".equals(n.url())),
                "隐藏功能页必须下发，否则产品详情会被前端守卫判成 404");
        assertTrue(flat.stream().anyMatch(n -> "TOOLS_PROFIT".equals(n.routeName())),
                "利润计算器菜单应在超管菜单树里");
    }

    @Test
    @DisplayName("不带 token 访问业务接口 → 业务 code 401")
    void noTokenIsRejected() throws Exception {
        AuthResult result = call("GET", "/api/product/list", null);
        assertFalse(result.passed());
        assertTrue(result.body().contains("\"code\":401"), result.body());
    }

    @Test
    @DisplayName("超管带 token 访问业务接口 → 放行且上下文标记为超管")
    void superAdminCanAccessBusinessApi() throws Exception {
        String token = issueToken(superAdmin().getId());
        AuthResult result = call("GET", "/api/product/list", token);
        assertTrue(result.passed(), result.body());
        assertTrue(result.superAdmin());
    }

    @Test
    @DisplayName("普通角色访问 /api/system/** → 403")
    void systemApiRejectsNormalRole() throws Exception {
        SysUser viewer = createViewer(true, 1);
        String token = issueToken(viewer.getId());
        AuthResult result = call("GET", "/api/system/user/list", token);
        assertFalse(result.passed());
        assertTrue(result.body().contains("\"code\":403"), result.body());
    }

    @Test
    @DisplayName("只读角色改产品 → 403；但 profit/calc 只算不写，必须放行")
    void readOnlyRoleBlocksWriteButAllowsCalc() throws Exception {
        SysUser viewer = createViewer(true, 0);   // 产品列表设为只读
        String token = issueToken(viewer.getId());

        AuthResult write = call("POST", "/api/product/update", token);
        assertFalse(write.passed());
        assertTrue(write.body().contains("\"code\":403"), write.body());

        // calc 是 POST 但只试算不写库，不在任何菜单的 write_api_patterns 里，必须放行
        AuthResult calc = call("POST", "/api/profit/calc", token);
        assertTrue(calc.passed(), "试算接口不该被只读权限拦下：" + calc.body());
    }

    @Test
    @DisplayName("没勾「录入产品」的角色调 create → 也拦（rm 为 null 不能放行）")
    void ungrantedMenuWriteIsBlocked() throws Exception {
        SysUser viewer = createViewer(true, 1);   // 只勾了产品列表，且是可编辑
        String token = issueToken(viewer.getId());
        AuthResult result = call("POST", "/api/product/create", token);
        assertFalse(result.passed());
        assertTrue(result.body().contains("\"code\":403"), result.body());
    }

    @Test
    @DisplayName("没分配角色的用户 → 业务接口挡住，但 /api/auth/** 仍可用")
    void noRoleUserIsRestricted() throws Exception {
        SysUser user = new SysUser();
        user.setUsername("test_norole");
        user.setPasswordHash(encoder.encode("123456"));
        user.setStatus(1);
        userMapper.insert(user);
        String token = issueToken(user.getId());

        assertFalse(call("GET", "/api/product/list", token).passed());
        assertTrue(call("POST", "/api/auth/change-password", token).passed());
    }

    @Test
    @DisplayName("普通角色菜单树不含系统管理组，且只读标记和隐藏页继承正确")
    void viewerMenuTree() {
        SysUser viewer = createViewer(true, 0);
        SysRole role = rbacQueryService.findRoleByUserId(viewer.getId());
        List<MenuTreeNodeVO> flat = flatten(rbacQueryService.buildMenuTree(role, false));

        assertTrue(flat.stream().noneMatch(n -> n.routeName() != null
                        && n.routeName().startsWith("SYSTEM_")),
                "系统管理菜单不能下发给普通角色");
        assertTrue(flat.stream().anyMatch(n -> "PRODUCT_ROOT".equals(n.routeName())),
                "被勾菜单的祖先目录要自动补齐，否则前端建树会丢层级");
        assertTrue(flat.stream().anyMatch(n -> "/product/detail".equals(n.url())),
                "隐藏页应跟随父菜单一起下发");
        MenuTreeNodeVO productList = flat.stream()
                .filter(n -> "PRODUCT_LIST".equals(n.routeName()))
                .findFirst().orElseThrow();
        assertEquals(0, productList.permType(), "产品列表应标记为只读");
    }

    @Test
    @DisplayName("内置超管角色不可删除")
    void builtinRoleCannotBeDeleted() {
        assertThrows(CYXYException.class, () -> sysRoleService.delete(1L));
    }

    @Test
    @DisplayName("改自己密码后令牌被清空，旧会话立即失效")
    void changePasswordClearsToken() {
        SysUser admin = superAdmin();
        issueToken(admin.getId());
        UserContext.set(new AuthUser(admin, rbacQueryService.findRoleByUserId(admin.getId())));
        authService.changePassword(new ChangePwdDTO("123456", "newpass123"));

        SysUser reloaded = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, admin.getId()));
        assertTrue(reloaded.getToken() == null,
                "改密后 token 必须清成 null（updateById 清不掉 null，必须用 wrapper）");
        assertTrue(encoder.matches("newpass123", reloaded.getPasswordHash()));
    }
}
