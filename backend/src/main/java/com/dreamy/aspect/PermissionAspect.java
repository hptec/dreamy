package com.dreamy.aspect;

import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.infra.grpc.IdentityGateClient;
import com.dreamy.infra.grpc.IdentityUnavailableException;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务端 RBAC 校验切面(P4 切换版)。
 * 通道开关 IDENTITY_GRPC_ENABLED:
 * - true(切换后):IdentityGate gRPC 实时权限解析(变更下一请求生效语义不变);
 * - false(回滚态):直查 DB(原 v1 语义)。
 */
@Aspect
@Component
@Order(0)
public class PermissionAspect {

    private final boolean grpcEnabled;
    private final IdentityGateClient identityGate;
    private final AdminUserMapper adminUserMapper;
    private final RoleMapper roleMapper;
    private final com.dreamy.domain.role.service.RoleService roleService;

    public PermissionAspect(
            @Value("${IDENTITY_GRPC_ENABLED:false}") boolean grpcEnabled,
            IdentityGateClient identityGate,
            AdminUserMapper adminUserMapper,
            RoleMapper roleMapper,
            com.dreamy.domain.role.service.RoleService roleService) {
        this.grpcEnabled = grpcEnabled;
        this.identityGate = identityGate;
        this.adminUserMapper = adminUserMapper;
        this.roleMapper = roleMapper;
        this.roleService = roleService;
    }

    @Before("@annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        List<String> keys = resolvePermissions(principal.subject());
        if (!keys.contains(requirePermission.value())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    /** 实时查 admin→role→permissionKeys(变更下一请求即生效) */
    public List<String> resolvePermissions(String adminId) {
        if (grpcEnabled) {
            try {
                return identityGate.resolvePermissions(Long.parseLong(adminId));
            } catch (IdentityUnavailableException e) {
                throw e; // 过滤器/全局处理器转 503
            }
        }
        // 回滚态:直查(原 v1)
        AdminUser admin = adminUserMapper.selectById(Long.parseLong(adminId));
        if (admin == null) {
            return List.of();
        }
        com.dreamy.domain.role.entity.Role role = roleMapper.selectById(admin.getRoleId());
        return roleService.effectivePermissionKeys(role);
    }
}
