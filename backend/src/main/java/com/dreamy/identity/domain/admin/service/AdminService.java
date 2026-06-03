package com.dreamy.identity.domain.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.identity.dto.AdminDTO;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.infra.AdminSessionValidityCache;
import com.dreamy.identity.domain.session.entity.AdminSessionEntity;
import com.dreamy.identity.domain.admin.entity.AdminUserEntity;
import com.dreamy.identity.domain.role.entity.RoleEntity;
import com.dreamy.identity.domain.session.repository.AdminSessionMapper;
import com.dreamy.identity.domain.admin.repository.AdminUserMapper;
import com.dreamy.identity.domain.role.repository.RoleMapper;
import com.dreamy.identity.domain.role.service.RoleService;
import com.dreamy.identity.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员领域服务（登录 FLOW-09 / CRUD FLOW-10）。
 * 约束: RM-050~053、RM-090；EDGE-011~014；超管保护 40306；删自己 40307；CV-003/004。
 */
@Service
public class AdminService {

    private final AdminUserMapper adminUserMapper;
    private final AdminSessionMapper adminSessionMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AdminSessionValidityCache validityCache;

    public AdminService(AdminUserMapper adminUserMapper,
                        AdminSessionMapper adminSessionMapper,
                        RoleMapper roleMapper,
                        RoleService roleService,
                        JwtTokenProvider jwtTokenProvider,
                        PasswordEncoder passwordEncoder,
                        AdminSessionValidityCache validityCache) {
        this.adminUserMapper = adminUserMapper;
        this.adminSessionMapper = adminSessionMapper;
        this.roleMapper = roleMapper;
        this.roleService = roleService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.validityCache = validityCache;
    }

    /**
     * FLOW-09 adminLogin。
     * 约束: STEP-01 凭据错 40103；STEP-02 禁用 40302；STEP-03 BCrypt；STEP-04 签发 8h JWT + 权限；STEP-05 审计由 Controller AOP。
     */
    @Transactional
    public LoginOutcome login(String email, String password, String ip, String device) {
        AdminUserEntity admin = findByEmail(email);
        if (admin == null) {
            throw new BizException(ErrorCode.CREDENTIALS_INVALID); // 40103
        }
        if ("disabled".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.ADMIN_DISABLED); // 40302
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BizException(ErrorCode.CREDENTIALS_INVALID); // 40103
        }
        RoleEntity role = roleMapper.selectById(admin.getRoleId());
        List<String> permissionKeys = roleService.effectivePermissionKeys(role);

        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken(
                String.valueOf(admin.getId()), String.valueOf(admin.getRoleId()));

        LocalDateTime now = LocalDateTime.now();
        AdminSessionEntity session = new AdminSessionEntity();
        session.setAdminId(admin.getId());
        session.setTokenId(token.tokenId());
        session.setIp(ip);
        session.setDevice(device);
        session.setStatus("active");
        session.setLastActiveAt(now);
        adminSessionMapper.insert(session);

        admin.setLastLoginAt(now);
        adminUserMapper.updateById(admin);

        // BLOCKER-1：事务提交后写 admin 会话有效性键（避免事务回滚后残留有效键）
        afterCommit(() -> validityCache.markValid(token.tokenId()));

        return new LoginOutcome(admin, role, permissionKeys, token.token());
    }

    /** adminLogout：撤销当前 jti 会话 */
    @Transactional
    public void logout(String tokenId) {
        LambdaQueryWrapper<AdminSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSessionEntity::getTokenId, tokenId);
        AdminSessionEntity session = adminSessionMapper.selectOne(qw);
        if (session != null) {
            session.setStatus("revoked");
            adminSessionMapper.updateById(session);
        }
        // BLOCKER-1：DEL 有效性键，下次请求即 401（即时失效）
        afterCommit(() -> validityCache.invalidate(tokenId));
    }

    /** adminMe / RBAC 守卫：校验 admin token 会话有效 + 返回 admin */
    public AdminUserEntity requireActiveAdmin(Long adminId, String tokenId) {
        AdminUserEntity admin = adminUserMapper.selectById(adminId);
        if (admin == null || "disabled".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        LambdaQueryWrapper<AdminSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSessionEntity::getTokenId, tokenId)
                .eq(AdminSessionEntity::getStatus, "active");
        if (adminSessionMapper.selectCount(qw) == 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return admin;
    }

    public AdminUserEntity findById(Long id) {
        return adminUserMapper.selectById(id);
    }

    public AdminUserEntity findByEmail(String email) {
        LambdaQueryWrapper<AdminUserEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminUserEntity::getEmail, email).last("LIMIT 1");
        return adminUserMapper.selectOne(qw);
    }

    /** RM-053 pageByFilter（listAdmins） */
    public IPage<AdminUserEntity> pageAdmins(int page, int pageSize, String status, Long roleId) {
        LambdaQueryWrapper<AdminUserEntity> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(AdminUserEntity::getStatus, status);
        }
        if (roleId != null) {
            qw.eq(AdminUserEntity::getRoleId, roleId);
        }
        qw.orderByDesc(AdminUserEntity::getCreatedAt);
        return adminUserMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** FLOW-10 createAdmin（FUNC-015 EDGE-012）：STEP-01 邮箱重复 40901；STEP-02 INSERT BCrypt */
    @Transactional
    public AdminUserEntity createAdmin(String name, String email, String password, Long roleId) {
        if (findByEmail(email) != null) {
            throw new BizException(ErrorCode.EMAIL_EXISTS); // 40901
        }
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR); // RI-001 role 不存在
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setName(name);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRoleId(roleId);
        admin.setStatus("active");
        admin.setVersion(0);
        adminUserMapper.insert(admin);
        return admin;
    }

    /** FLOW-10 updateAdmin（FUNC-016）：改 name/role_id；超管降权 40306（EDGE-014） */
    @Transactional
    public AdminUserEntity updateAdmin(Long id, String name, Long roleId) {
        AdminUserEntity admin = requireExist(id);
        if (isSuperAdmin(admin) && roleId != null && !roleId.equals(admin.getRoleId())) {
            throw new BizException(ErrorCode.SUPER_ADMIN_PROTECTED); // 40306
        }
        if (name != null) {
            admin.setName(name);
        }
        if (roleId != null) {
            if (roleMapper.selectById(roleId) == null) {
                throw new BizException(ErrorCode.VALIDATION_ERROR);
            }
            admin.setRoleId(roleId);
        }
        adminUserMapper.updateById(admin);
        return admin;
    }

    /** FLOW-10 deleteAdmin（FUNC-017 EDGE-013/014）：STEP-01 删自己 40307；STEP-02 超管 40306；STEP-03 DELETE */
    @Transactional
    public void deleteAdmin(Long id, Long currentAdminId) {
        if (id.equals(currentAdminId)) {
            throw new BizException(ErrorCode.CANNOT_DELETE_SELF); // 40307
        }
        AdminUserEntity admin = requireExist(id);
        if (isSuperAdmin(admin)) {
            throw new BizException(ErrorCode.SUPER_ADMIN_PROTECTED); // 40306
        }
        adminUserMapper.deleteById(id);
    }

    /** toggleAdminStatus（EDGE-014）：超管 40306；禁用级联 revoke admin_session */
    @Transactional
    public AdminUserEntity toggleStatus(Long id, String status) {
        AdminUserEntity admin = requireExist(id);
        if (isSuperAdmin(admin)) {
            throw new BizException(ErrorCode.SUPER_ADMIN_PROTECTED); // 40306
        }
        admin.setStatus(status);
        adminUserMapper.updateById(admin);
        if ("disabled".equals(status)) {
            revokeAdminSessions(id);
        }
        return admin;
    }

    /** resetAdminPassword：UPDATE password_hash（CV-004 ≥6 由 Controller 校验） */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AdminUserEntity admin = requireExist(id);
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserMapper.updateById(admin);
    }

    /** RM-090 revokeAllByAdminId（禁用级联） */
    private void revokeAdminSessions(Long adminId) {
        LambdaQueryWrapper<AdminSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSessionEntity::getAdminId, adminId)
                .eq(AdminSessionEntity::getStatus, "active");
        for (AdminSessionEntity session : adminSessionMapper.selectList(qw)) {
            session.setStatus("revoked");
            adminSessionMapper.updateById(session);
            // BLOCKER-1：禁用管理员级联撤销，DEL Redis 单级键即时失效（EDGE-014）
            String tokenId = session.getTokenId();
            afterCommit(() -> validityCache.invalidate(tokenId));
        }
    }

    private AdminUserEntity requireExist(Long id) {
        AdminUserEntity admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return admin;
    }

    /** 超管判定：所属 role.is_locked=true */
    public boolean isSuperAdmin(AdminUserEntity admin) {
        RoleEntity role = roleMapper.selectById(admin.getRoleId());
        return role != null && Boolean.TRUE.equals(role.getIsLocked());
    }

    /** 事务提交后回调；无事务上下文时直接执行（与 SessionService 一致） */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // ===== 表示层 DTO 组装（Controller 不接触 Entity）=====

    /** AdminUser→AdminDTO（补 role_name；隐藏 password_hash） */
    public AdminDTO toAdminDTO(AdminUserEntity admin) {
        RoleEntity role = roleMapper.selectById(admin.getRoleId());
        return new AdminDTO(admin.getId(), admin.getName(), admin.getEmail(), admin.getRoleId(),
                role != null ? role.getName() : null, admin.getStatus(), admin.getLastLoginAt());
    }

    /** adminMe 数据组装（role_name + is_super + 实时 permissionKeys） */
    public MeData meData(Long adminId, String tokenId) {
        AdminUserEntity admin = requireActiveAdmin(adminId, tokenId);
        RoleEntity role = roleMapper.selectById(admin.getRoleId());
        boolean isSuper = role != null && Boolean.TRUE.equals(role.getIsLocked());
        List<String> permissionKeys = roleService.effectivePermissionKeys(role);
        return new MeData(toAdminDTO(admin), role != null ? role.getName() : "", isSuper, permissionKeys);
    }

    /** 独立权限查询：实时返回当前管理员有效权限（前端刷新即生效，无需重登） */
    public List<String> currentPermissions(Long adminId, String tokenId) {
        AdminUserEntity admin = requireActiveAdmin(adminId, tokenId);
        RoleEntity role = roleMapper.selectById(admin.getRoleId());
        return roleService.effectivePermissionKeys(role);
    }

    /** 分页管理员 DTO（含 total/分页元数据） */
    public PageData<AdminDTO> pageAdminDTOs(int page, int pageSize, String status, Long roleId) {
        IPage<AdminUserEntity> pg = pageAdmins(page, pageSize, status, roleId);
        List<AdminDTO> items = pg.getRecords().stream().map(this::toAdminDTO).toList();
        return new PageData<>(items, pg.getTotal(), page, pageSize);
    }

    /** create/update/toggle 后返回 DTO */
    public AdminDTO createAdminDTO(String name, String email, String password, Long roleId) {
        return toAdminDTO(createAdmin(name, email, password, roleId));
    }

    public AdminDTO updateAdminDTO(Long id, String name, Long roleId) {
        return toAdminDTO(updateAdmin(id, name, roleId));
    }

    public AdminDTO toggleStatusDTO(Long id, String status) {
        return toAdminDTO(toggleStatus(id, status));
    }

    public record MeData(AdminDTO admin, String roleName, boolean isSuper, List<String> permissionKeys) {}

    public record PageData<T>(List<T> items, long total, int page, int pageSize) {}

    public record LoginOutcome(AdminUserEntity admin, RoleEntity role,
                               List<String> permissionKeys, String token) {
        /** 表示层：登录管理员 DTO（含 role_name） */
        public AdminDTO adminDTO() {
            return new AdminDTO(admin.getId(), admin.getName(), admin.getEmail(), admin.getRoleId(),
                    role != null ? role.getName() : null, admin.getStatus(), admin.getLastLoginAt());
        }
    }
}
