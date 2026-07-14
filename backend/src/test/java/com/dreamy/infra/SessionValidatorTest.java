package com.dreamy.infra;

import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.session.entity.AdminSession;
import com.dreamy.domain.session.repository.AdminSessionMapper;
import com.dreamy.domain.session.repository.UserSessionMapper;
import com.dreamy.enums.AdminStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionValidatorTest {

    @Mock UserSessionMapper userSessionMapper;
    @Mock AdminSessionMapper adminSessionMapper;
    @Mock AdminUserMapper adminUserMapper;

    private SessionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SessionValidator(userSessionMapper, adminSessionMapper, adminUserMapper);
    }

    @Test
    @DisplayName("管理员会话仅以 DB active 状态为授权依据")
    void activeAdminSessionUsesAuthoritativeDbState() {
        AdminSession session = new AdminSession();
        session.setAdminId(42L);
        AdminUser admin = new AdminUser();
        admin.setStatus(AdminStatus.ACTIVE);
        when(adminSessionMapper.selectOne(any())).thenReturn(session);
        when(adminUserMapper.selectById(42L)).thenReturn(admin);

        assertThat(validator.isAdminSessionValid("token")).isTrue();
    }

    @Test
    @DisplayName("已撤销的管理员 DB 会话不能放行")
    void revokedAdminSessionIsRejected() {
        when(adminSessionMapper.selectOne(any())).thenReturn(null);

        assertThat(validator.isAdminSessionValid("token")).isFalse();

        verify(adminUserMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("已撤销的 Store DB 会话不能放行")
    void revokedStoreSessionIsRejected() {
        when(userSessionMapper.selectCount(any())).thenReturn(0L);

        assertThat(validator.isStoreSessionValid("token")).isFalse();
    }

    @Test
    @DisplayName("Store DB 会话 active 时允许请求")
    void activeStoreSessionIsAccepted() {
        when(userSessionMapper.selectCount(any())).thenReturn(1L);

        assertThat(validator.isStoreSessionValid("token")).isTrue();
    }

    @Test
    @DisplayName("孤儿 active 管理员会话按 fail-closed 拒绝")
    void orphanAdminSessionIsRejected() {
        AdminSession session = new AdminSession();
        session.setAdminId(42L);
        when(adminSessionMapper.selectOne(any())).thenReturn(session);
        when(adminUserMapper.selectById(42L)).thenReturn(null);

        assertThat(validator.isAdminSessionValid("token")).isFalse();
    }

    @Test
    @DisplayName("已禁用管理员的 active 会话不能放行")
    void disabledAdminSessionIsRejected() {
        AdminSession session = new AdminSession();
        session.setAdminId(42L);
        AdminUser admin = new AdminUser();
        admin.setStatus(AdminStatus.DISABLED);
        when(adminSessionMapper.selectOne(any())).thenReturn(session);
        when(adminUserMapper.selectById(42L)).thenReturn(admin);

        assertThat(validator.isAdminSessionValid("token")).isFalse();
    }
}
