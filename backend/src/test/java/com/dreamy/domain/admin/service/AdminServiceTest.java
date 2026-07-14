package com.dreamy.domain.admin.service;

import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.service.RoleService;
import com.dreamy.domain.session.entity.AdminSession;
import com.dreamy.domain.session.repository.AdminSessionMapper;
import com.dreamy.enums.AdminStatus;
import com.dreamy.enums.SessionStatus;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.infra.AdminSessionValidityCache;
import com.dreamy.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock AdminUserMapper adminUserMapper;
    @Mock AdminSessionMapper adminSessionMapper;
    @Mock RoleMapper roleMapper;
    @Mock RoleService roleService;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AdminSessionValidityCache validityCache;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(adminUserMapper, adminSessionMapper, roleMapper, roleService,
                jwtTokenProvider, passwordEncoder, validityCache);
    }

    @Test
    @DisplayName("删除普通管理员：先清理会话再物理删除，保留弱引用审计日志")
    void deleteAdminCleansSessionsBeforePhysicalDelete() {
        AdminUser target = admin(2L, 9L);
        Role role = role(false);
        AdminSession active = session("token-active");
        AdminSession revoked = session("token-revoked");
        when(adminUserMapper.selectByIdForUpdate(2L)).thenReturn(target);
        when(roleMapper.selectById(9L)).thenReturn(role);
        when(adminSessionMapper.selectList(any())).thenReturn(List.of(active, revoked));

        service.deleteAdmin(2L, 1L);

        InOrder deletion = inOrder(adminSessionMapper, adminUserMapper);
        deletion.verify(adminSessionMapper).selectList(any());
        deletion.verify(adminSessionMapper).delete(any());
        deletion.verify(adminUserMapper).deleteById(2L);
        verify(validityCache).invalidate("token-active");
        verify(validityCache).invalidate("token-revoked");
        verify(adminUserMapper, never()).updateById(any(AdminUser.class));
    }

    @Test
    @DisplayName("删除自己在查库前被拒绝")
    void deleteAdminRejectsSelfBeforeMutation() {
        assertThatThrownBy(() -> service.deleteAdmin(1L, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CANNOT_DELETE_SELF));

        verify(adminUserMapper, never()).selectByIdForUpdate(any());
        verify(adminSessionMapper, never()).delete(any());
        verify(adminUserMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("超级管理员不可删除，不清理会话也不删主记录")
    void deleteAdminRejectsSuperAdminWithoutMutation() {
        when(adminUserMapper.selectByIdForUpdate(2L)).thenReturn(admin(2L, 9L));
        when(roleMapper.selectById(9L)).thenReturn(role(true));

        assertThatThrownBy(() -> service.deleteAdmin(2L, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SUPER_ADMIN_PROTECTED));

        verify(adminSessionMapper, never()).selectList(any());
        verify(adminSessionMapper, never()).delete(any());
        verify(adminUserMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("登录使用管理员行锁，并在事务提交前发布会话缓存")
    void loginLocksAdminAndPublishesSessionBeforeCommit() {
        AdminUser target = admin(2L, 9L);
        target.setEmail("ops@dreamy.com");
        target.setPasswordHash("hash");
        target.setStatus(AdminStatus.ACTIVE);
        target.setVersion(0);
        Role role = role(false);
        role.setId(9L);
        JwtTokenProvider.AdminToken token = new JwtTokenProvider.AdminToken(
                "jwt", "token-login", LocalDateTime.now().plusHours(8));
        when(adminUserMapper.selectByEmailForUpdate("ops@dreamy.com")).thenReturn(target);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(roleMapper.selectById(9L)).thenReturn(role);
        when(roleService.effectivePermissionKeys(role)).thenReturn(List.of("/system/admins"));
        when(jwtTokenProvider.issueAdminToken("2", "9")).thenReturn(token);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            AdminService.LoginOutcome outcome = service.login(
                    "ops@dreamy.com", "secret", "127.0.0.1", "test");

            assertThat(outcome.token()).isEqualTo("jwt");
            verify(adminSessionMapper).insert(any(AdminSession.class));
            verify(validityCache, never()).markValid(any());

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.getFirst().beforeCommit(false);
            verify(validityCache).markValid("token-login");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("禁用管理员使用同一行锁并撤销既有会话")
    void disableAdminLocksRowAndRevokesSessions() {
        AdminUser target = admin(2L, 9L);
        target.setStatus(AdminStatus.ACTIVE);
        AdminSession active = session("token-active");
        active.setStatus(SessionStatus.ACTIVE);
        when(adminUserMapper.selectByIdForUpdate(2L)).thenReturn(target);
        when(roleMapper.selectById(9L)).thenReturn(role(false));
        when(adminSessionMapper.selectList(any())).thenReturn(List.of(active));

        service.toggleStatus(2L, AdminStatus.DISABLED);

        assertThat(target.getStatus()).isEqualTo(AdminStatus.DISABLED);
        assertThat(active.getStatus()).isEqualTo(SessionStatus.REVOKED);
        verify(adminUserMapper).updateById(target);
        verify(adminSessionMapper).updateById(active);
        verify(validityCache).invalidate("token-active");
    }

    @Test
    @DisplayName("重置管理员密码使用行锁且保留既有会话行为")
    void resetPasswordLocksRowWithoutChangingSessionBehavior() {
        AdminUser target = admin(2L, 9L);
        when(adminUserMapper.selectByIdForUpdate(2L)).thenReturn(target);
        when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");

        service.resetPassword(2L, "new-secret");

        assertThat(target.getPasswordHash()).isEqualTo("new-hash");
        verify(adminUserMapper).updateById(target);
        verify(adminSessionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("已物理删除的管理员不能凭孤儿会话继续通过活跃校验")
    void missingAdminIsUnauthorizedEvenIfSessionCouldRemain() {
        when(adminUserMapper.selectById(2L)).thenReturn(null);

        assertThatThrownBy(() -> service.requireActiveAdmin(2L, "orphan-token"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(adminSessionMapper, never()).selectCount(any());
    }

    private AdminUser admin(Long id, Long roleId) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        admin.setRoleId(roleId);
        return admin;
    }

    private Role role(boolean locked) {
        Role role = new Role();
        role.setIsLocked(locked);
        return role;
    }

    private AdminSession session(String tokenId) {
        AdminSession session = new AdminSession();
        session.setTokenId(tokenId);
        return session;
    }
}
