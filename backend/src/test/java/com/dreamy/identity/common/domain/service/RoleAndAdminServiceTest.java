package com.dreamy.identity.common.domain.service;

import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.domain.admin.entity.AdminUserEntity;
import com.dreamy.identity.domain.role.entity.RoleEntity;
import com.dreamy.identity.domain.admin.repository.AdminUserMapper;
import com.dreamy.identity.domain.admin.service.AdminService;
import com.dreamy.identity.domain.role.repository.PermissionMapper;
import com.dreamy.identity.domain.role.repository.RoleMapper;
import com.dreamy.identity.domain.role.repository.RolePermissionMapper;
import com.dreamy.identity.domain.role.service.RoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UT-05 Role 超管 is_locked 保护 + UT-07 AdminUser 删自己拒绝。
 * 约束: UT-05（改权限/删/降权拒绝 40306/40308）；UT-07（删自己 40307）；P0。
 */
@ExtendWith(MockitoExtension.class)
class RoleAndAdminServiceTest {

    @Mock RoleMapper roleMapper;
    @Mock RolePermissionMapper rolePermissionMapper;
    @Mock PermissionMapper permissionMapper;
    @Mock AdminUserMapper adminUserMapper;
    @InjectMocks RoleService roleService;

    @Mock com.dreamy.identity.domain.session.repository.AdminSessionMapper adminSessionMapper;
    @Mock com.dreamy.identity.security.JwtTokenProvider jwtTokenProvider;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock com.dreamy.identity.infra.AdminSessionValidityCache adminSessionValidityCache;

    @Test
    @DisplayName("TC-UNIT-030: 超管角色 is_locked → updateRole 40308 ROLE_LOCKED（EDGE-014）")
    void updateRole_lockedRole_throws40308() {
        RoleEntity locked = lockedRole();
        when(roleMapper.selectById(100L)).thenReturn(locked);

        assertThatThrownBy(() -> roleService.updateRole(100L, null, java.util.List.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROLE_LOCKED));
    }

    @Test
    @DisplayName("TC-UNIT-031: 超管角色 is_locked → deleteRole 40308 ROLE_LOCKED（EDGE-014）")
    void deleteRole_lockedRole_throws40308() {
        when(roleMapper.selectById(100L)).thenReturn(lockedRole());

        assertThatThrownBy(() -> roleService.deleteRole(100L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROLE_LOCKED));
    }

    @Test
    @DisplayName("TC-UNIT-032: 角色有成员 → deleteRole 40904 ROLE_IN_USE（EDGE-015）")
    void deleteRole_hasMembers_throws40904() {
        RoleEntity custom = customRole();
        when(roleMapper.selectById(1L)).thenReturn(custom);
        when(adminUserMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> roleService.deleteRole(1L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ROLE_IN_USE));
    }

    @Test
    @DisplayName("TC-UNIT-033: deleteAdmin 删自己 → 40307 CANNOT_DELETE_SELF（EDGE-013）")
    void deleteAdmin_self_throws40307() {
        AdminService adminService = new AdminService(adminUserMapper, adminSessionMapper,
                roleMapper, roleService, jwtTokenProvider, passwordEncoder, adminSessionValidityCache);

        assertThatThrownBy(() -> adminService.deleteAdmin(1L, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CANNOT_DELETE_SELF));
    }

    private RoleEntity lockedRole() {
        RoleEntity r = new RoleEntity();
        r.setId(100L);
        r.setName("超级管理员");
        r.setType("preset");
        r.setIsLocked(true);
        return r;
    }

    private RoleEntity customRole() {
        RoleEntity r = new RoleEntity();
        r.setId(1L);
        r.setName("运营");
        r.setType("custom");
        r.setIsLocked(false);
        return r;
    }
}
