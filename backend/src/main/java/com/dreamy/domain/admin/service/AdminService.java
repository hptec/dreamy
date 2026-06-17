package com.dreamy.domain.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.AdminStatus;
import com.dreamy.enums.SessionStatus;
import com.dreamy.dto.AdminDTO;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.infra.AdminSessionValidityCache;
import com.dreamy.domain.session.entity.AdminSession;
import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.session.repository.AdminSessionMapper;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.service.RoleService;
import com.dreamy.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

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

    @Transactional
    public LoginOutcome login(String email, String password, String ip, String device) {
        AdminUser admin = findByEmail(email);
        if (admin == null) {
            throw new BizException(ErrorCode.CREDENTIALS_INVALID);
        }
        if (admin.getStatus() == AdminStatus.DISABLED) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BizException(ErrorCode.CREDENTIALS_INVALID);
        }
        Role role = roleMapper.selectById(admin.getRoleId());
        List<String> permissionKeys = roleService.effectivePermissionKeys(role);

        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken(
                String.valueOf(admin.getId()), String.valueOf(admin.getRoleId()));

        LocalDateTime now = LocalDateTime.now();
        AdminSession session = new AdminSession();
        session.setAdminId(admin.getId());
        session.setTokenId(token.tokenId());
        session.setIp(ip);
        session.setDevice(device);
        session.setStatus(SessionStatus.ACTIVE);
        session.setLastActiveAt(now);
        adminSessionMapper.insert(session);

        admin.setLastLoginAt(now);
        adminUserMapper.updateById(admin);

        afterCommit(() -> validityCache.markValid(token.tokenId()));
        return new LoginOutcome(admin, role, permissionKeys, token.token());
    }

    @Transactional
    public void logout(String tokenId) {
        LambdaQueryWrapper<AdminSession> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSession::getTokenId, tokenId);
        AdminSession session = adminSessionMapper.selectOne(qw);
        if (session != null) {
            session.setStatus(SessionStatus.REVOKED);
            adminSessionMapper.updateById(session);
        }
        afterCommit(() -> validityCache.invalidate(tokenId));
    }

    public AdminUser requireActiveAdmin(Long adminId, String tokenId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null || admin.getStatus() == AdminStatus.DISABLED) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        LambdaQueryWrapper<AdminSession> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSession::getTokenId, tokenId)
                .eq(AdminSession::getStatus, SessionStatus.ACTIVE);
        if (adminSessionMapper.selectCount(qw) == 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return admin;
    }

    public AdminUser findById(Long id) {
        return adminUserMapper.selectById(id);
    }

    public AdminUser findByEmail(String email) {
        LambdaQueryWrapper<AdminUser> qw = new LambdaQueryWrapper<>();
        qw.isNull(AdminUser::getDeletedAt);
        qw.eq(AdminUser::getEmail, email).last("LIMIT 1");
        return adminUserMapper.selectOne(qw);
    }

    public IPage<AdminUser> pageAdmins(int page, int pageSize, AdminStatus status, Long roleId) {
        LambdaQueryWrapper<AdminUser> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(AdminUser::getStatus, status);
        }
        if (roleId != null) {
            qw.eq(AdminUser::getRoleId, roleId);
        }
        qw.orderByDesc(AdminUser::getCreatedAt);
        return adminUserMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    @Transactional
    public AdminUser createAdmin(String name, String email, String password, Long roleId) {
        if (findByEmail(email) != null) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        AdminUser admin = new AdminUser();
        admin.setName(name);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRoleId(roleId);
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setVersion(0);
        adminUserMapper.insert(admin);
        return admin;
    }

    @Transactional
    public AdminUser updateAdmin(Long id, String name, Long roleId) {
        AdminUser admin = requireExist(id);
        if (isSuperAdmin(admin) && roleId != null && !roleId.equals(admin.getRoleId())) {
            throw new BizException(ErrorCode.SUPER_ADMIN_PROTECTED);
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

    @Transactional
    public void deleteAdmin(Long id, Long currentAdminId) {
        if (id.equals(currentAdminId)) {
            throw new BizException(ErrorCode.CANNOT_DELETE_SELF);
        }
        AdminUser admin = requireExist(id);
        if (isSuperAdmin(admin)) {
            throw new BizException(ErrorCode.SUPER_ADMIN_PROTECTED);
        }
        // 逻辑删除：设置 deleted_at = now()
        AdminUser patch = new AdminUser();
        patch.setId(id);
        patch.setDeletedAt(LocalDateTime.now());
        adminUserMapper.updateById(patch);
    }

    @Transactional
    public AdminUser toggleStatus(Long id, AdminStatus status) {
        AdminUser admin = requireExist(id);
        if (isSuperAdmin(admin)) {
            throw new BizException(ErrorCode.SUPER_ADMIN_PROTECTED);
        }
        admin.setStatus(status);
        adminUserMapper.updateById(admin);
        if (status == AdminStatus.DISABLED) {
            revokeAdminSessions(id);
        }
        return admin;
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AdminUser admin = requireExist(id);
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserMapper.updateById(admin);
    }

    private void revokeAdminSessions(Long adminId) {
        LambdaQueryWrapper<AdminSession> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSession::getAdminId, adminId)
                .eq(AdminSession::getStatus, SessionStatus.ACTIVE);
        for (AdminSession session : adminSessionMapper.selectList(qw)) {
            session.setStatus(SessionStatus.REVOKED);
            adminSessionMapper.updateById(session);
            String tokenId = session.getTokenId();
            afterCommit(() -> validityCache.invalidate(tokenId));
        }
    }

    private AdminUser requireExist(Long id) {
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return admin;
    }

    public boolean isSuperAdmin(AdminUser admin) {
        Role role = roleMapper.selectById(admin.getRoleId());
        return role != null && Boolean.TRUE.equals(role.getIsLocked());
    }

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

    public AdminDTO toAdminDTO(AdminUser admin) {
        Role role = roleMapper.selectById(admin.getRoleId());
        return new AdminDTO(admin.getId(), admin.getName(), admin.getEmail(), admin.getRoleId(),
                role != null ? role.getName() : null, admin.getStatus(), admin.getLastLoginAt());
    }

    public MeData meData(Long adminId, String tokenId) {
        AdminUser admin = requireActiveAdmin(adminId, tokenId);
        Role role = roleMapper.selectById(admin.getRoleId());
        boolean isSuper = role != null && Boolean.TRUE.equals(role.getIsLocked());
        List<String> permissionKeys = roleService.effectivePermissionKeys(role);
        return new MeData(toAdminDTO(admin), role != null ? role.getName() : "", isSuper, permissionKeys);
    }

    public List<String> currentPermissions(Long adminId, String tokenId) {
        AdminUser admin = requireActiveAdmin(adminId, tokenId);
        Role role = roleMapper.selectById(admin.getRoleId());
        return roleService.effectivePermissionKeys(role);
    }

    public PageData<AdminDTO> pageAdminDTOs(int page, int pageSize, AdminStatus status, Long roleId) {
        IPage<AdminUser> pg = pageAdmins(page, pageSize, status, roleId);
        List<AdminDTO> items = pg.getRecords().stream().map(this::toAdminDTO).toList();
        return new PageData<>(items, pg.getTotal(), page, pageSize);
    }

    public AdminDTO createAdminDTO(String name, String email, String password, Long roleId) {
        return toAdminDTO(createAdmin(name, email, password, roleId));
    }

    public AdminDTO updateAdminDTO(Long id, String name, Long roleId) {
        return toAdminDTO(updateAdmin(id, name, roleId));
    }

    public AdminDTO toggleStatusDTO(Long id, AdminStatus status) {
        return toAdminDTO(toggleStatus(id, status));
    }

    public record MeData(AdminDTO admin, String roleName, boolean isSuper, List<String> permissionKeys) {}

    public record PageData<T>(List<T> items, long total, int page, int pageSize) {}

    public record LoginOutcome(AdminUser admin, Role role,
                               List<String> permissionKeys, String token) {
        public AdminDTO adminDTO() {
            return new AdminDTO(admin.getId(), admin.getName(), admin.getEmail(), admin.getRoleId(),
                    role != null ? role.getName() : null, admin.getStatus(), admin.getLastLoginAt());
        }
    }
}
