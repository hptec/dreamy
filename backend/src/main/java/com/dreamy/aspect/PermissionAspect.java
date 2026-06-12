package com.dreamy.aspect;

import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.service.RoleService;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务端 RBAC 校验切面（BLOCKER-2）。
 * 约束: api-detail §0 RBAC；缺权限→403 40300；BE-DIM-6 服务端强制鉴权。
 * 每次请求实时按 adminId→roleId→permissionKeys 查 DB，权限变更立即生效，不依赖 token 快照。
 */
@Aspect
@Component
@Order(0)
public class PermissionAspect {

    private final AdminUserMapper adminUserMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;

    public PermissionAspect(AdminUserMapper adminUserMapper, RoleMapper roleMapper, RoleService roleService) {
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

    /** 实时查 admin→role→permissionKeys，权限变更下一请求即生效 */
    public List<String> resolvePermissions(String adminId) {
        AdminUser admin = adminUserMapper.selectById(Long.parseLong(adminId));
        if (admin == null) {
            return List.of();
        }
        Role role = roleMapper.selectById(admin.getRoleId());
        return roleService.effectivePermissionKeys(role);
    }
}
