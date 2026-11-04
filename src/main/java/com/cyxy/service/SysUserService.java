package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cyxy.common.AuthUser;
import com.cyxy.common.UserContext;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.SysRoleMapper;
import com.cyxy.mapper.SysUserMapper;
import com.cyxy.mapper.SysUserRoleMapper;
import com.cyxy.model.dto.ResetPwdDTO;
import com.cyxy.model.dto.SysUserSaveDTO;
import com.cyxy.model.entity.SysRole;
import com.cyxy.model.entity.SysUser;
import com.cyxy.model.entity.SysUserRole;
import com.cyxy.model.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 系统管理 - 用户管理。所有入口都在 /api/system/user/**，拦截器保证只有超管能进。
 *
 * 三道自我保护（防止手滑把系统锁死）：
 *   1) 不能删除自己；
 *   2) 不能修改自己的角色（超管把自己降权就再没人能进系统管理了）；
 *   3) 内置超管账号不能改角色、不能禁用、不能删除。
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final RbacQueryService rbacQueryService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户列表。角色名一次性查出来组装 map，避免每行再查一次库（N+1）。
     * 返回的是 SysUserVO，绝不含 passwordHash / token。
     */
    public List<SysUserVO> list(String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(SysUser::getUsername, kw).or().like(SysUser::getRealName, kw));
        }
        List<SysUser> users = userMapper.selectList(wrapper);

        // 关联表没有软删，selectList(null) 直接全量；roleMapper 会自动带 is_deleted=0
        Map<Long, Long> userIdToRoleId = userRoleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUserRole::getUserId, SysUserRole::getRoleId, (a, b) -> a));
        Map<Long, SysRole> roleById = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r, (a, b) -> a));

        return users.stream().map(u -> {
            Long roleId = userIdToRoleId.get(u.getId());
            SysRole role = roleId == null ? null : roleById.get(roleId);
            return new SysUserVO(u.getId(), u.getUsername(), u.getRealName(), u.getStatus(),
                    roleId, role == null ? null : role.getRoleName(),
                    u.getLastLoginTime(), u.getRemark(), u.getCreatedTime());
        }).toList();
    }

    /** 新建用户：账号 + BCrypt 初始密码 + 一个角色 */
    @Transactional
    public void create(SysUserSaveDTO dto) {
        String username = dto.username() == null ? "" : dto.username().trim();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(dto.password())) {
            throw new CYXYException(400, "账号和初始密码不能为空");
        }
        if (dto.password().length() < 6) {
            throw new CYXYException(400, "初始密码至少 6 位");
        }
        requireUsableRole(dto.roleId());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setRealName(dto.realName());
        user.setStatus(dto.status() == null ? 1 : dto.status());
        user.setRemark(dto.remark());

        // ⚠️ 库上唯一索引 uk_sys_user_username 只含 username，软删过的账号仍在表里，
        //    而 MyBatis-Plus 的 selectCount 会自动拼 is_deleted=0 查不到它。
        //    所以不靠「先查再插」，直接让唯一索引兜底，把冲突翻译成友好提示。
        try {
            userMapper.insert(user);       // @TableId(AUTO) 会把自增 id 回填进 user
        } catch (DuplicateKeyException e) {
            throw new CYXYException(400, "账号已存在（含已删除账号，请换一个）");
        }
        replaceRole(user.getId(), dto.roleId());
    }

    /**
     * 修改用户。
     * username 故意不开放修改：改账号会牵动唯一索引和已登录会话，3 个人用没这个必要。
     * password 为空表示不改密码。
     */
    @Transactional
    public void update(SysUserSaveDTO dto) {
        if (dto.id() == null) {
            throw new CYXYException(400, "缺少用户 ID");
        }
        SysUser existing = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, dto.id()));
        if (existing == null) {
            throw new CYXYException(400, "用户不存在");
        }

        SysRole targetRole = rbacQueryService.findRoleByUserId(dto.id());
        boolean targetIsSuper = targetRole != null && Integer.valueOf(1).equals(targetRole.getIsBuiltin());
        AuthUser me = UserContext.get();
        boolean isSelf = me != null && Objects.equals(me.user().getId(), dto.id());

        if (targetIsSuper) {
            if (dto.roleId() != null && !Objects.equals(dto.roleId(), targetRole.getId())) {
                throw new CYXYException(400, "内置超管账号不能修改角色");
            }
            if (Integer.valueOf(0).equals(dto.status())) {
                throw new CYXYException(400, "内置超管账号不能禁用");
            }
        }
        if (isSelf && dto.roleId() != null && me.role() != null
                && !Objects.equals(dto.roleId(), me.role().getId())) {
            throw new CYXYException(400, "不能修改自己的角色，避免把自己锁在系统外面");
        }

        SysUser patch = new SysUser();
        patch.setId(dto.id());
        patch.setRealName(dto.realName());
        patch.setStatus(dto.status());
        patch.setRemark(dto.remark());
        if (StringUtils.hasText(dto.password())) {
            if (dto.password().length() < 6) {
                throw new CYXYException(400, "新密码至少 6 位");
            }
            patch.setPasswordHash(passwordEncoder.encode(dto.password()));
            // 在这里改密码也要踢对方下线，否则旧 token 还能继续用
            clearToken(dto.id());
        }
        userMapper.updateById(patch);       // 只更新非 null 字段，不会动 username / token

        if (dto.roleId() != null) {
            replaceRole(dto.id(), dto.roleId());
        }
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new CYXYException(400, "缺少用户 ID");
        }
        AuthUser me = UserContext.get();
        if (me != null && Objects.equals(me.user().getId(), id)) {
            throw new CYXYException(400, "不能删除自己");
        }
        SysUser existing = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, id));
        if (existing == null) {
            throw new CYXYException(400, "用户不存在");
        }
        SysRole role = rbacQueryService.findRoleByUserId(id);
        if (role != null && Integer.valueOf(1).equals(role.getIsBuiltin())) {
            throw new CYXYException(400, "内置超管账号不能删除");
        }

        // 逻辑删除即可：拦截器认人时带 is_deleted=0，旧 token 立刻失效，不用再手动清
        userMapper.deleteById(id);
        // 关联表没有 is_deleted，直接物理删关联行
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    /** 超管重置任意用户密码：不需要旧密码，但要让对方所有会话立即失效 */
    @Transactional
    public void resetPassword(ResetPwdDTO dto) {
        if (dto == null || dto.userId() == null || !StringUtils.hasText(dto.newPwd())
                || dto.newPwd().length() < 6) {
            throw new CYXYException(400, "参数不完整或新密码不足 6 位");
        }
        SysUser target = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, dto.userId()));
        if (target == null) {
            throw new CYXYException(400, "用户不存在");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, dto.userId())
                .set(SysUser::getPasswordHash, passwordEncoder.encode(dto.newPwd())));
        clearToken(dto.userId());
    }

    /**
     * 清空令牌。必须用 LambdaUpdateWrapper.set(..., null)：
     * updateById 默认忽略 null 字段，传 null 是清不掉的。
     */
    private void clearToken(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getToken, null)
                .set(SysUser::getTokenExpireTime, null));
    }

    /** 校验角色可用：不存在、被停用、被软删都不允许分配 */
    private SysRole requireUsableRole(Long roleId) {
        if (roleId == null) {
            throw new CYXYException(400, "请选择角色");
        }
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getId, roleId)
                .eq(SysRole::getStatus, 1));
        if (role == null) {
            throw new CYXYException(400, "角色不存在或已停用");
        }
        return role;
    }

    /** 换角色：关联表没有软删，先物理删旧行再插新的；调用方必须包在事务里 */
    private void replaceRole(Long userId, Long roleId) {
        requireUsableRole(roleId);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        userRoleMapper.insert(relation);
    }
}
