package com.dreamy.identity.admin.aspect;

import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.security.AuthContext;
import com.dreamy.identity.common.security.AuthPrincipal;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务端 RBAC 校验切面（BLOCKER-2）。
 * 约束: api-detail §0 RBAC；缺权限→403 40300；BE-DIM-6 服务端强制鉴权。
 *
 * 在标注 @RequirePermission 的方法执行前校验：
 * 1. AuthContext 无 principal（理论上过滤器已拦截）→ 40100 UNAUTHORIZED。
 * 2. principal.permissionKeys 不含要求的 menu key → 40300 FORBIDDEN。
 * 超管 permission_keys 含全部 22 项（RoleService.effectivePermissionKeys 短路），自然通过。
 *
 * Order 设为低于 AuditAspect 默认值，确保权限校验先于审计执行（@Before 早于 @Around proceed 之后写审计；
 * 但越权应在业务执行前短路，故用 @Before + 较高优先级 Order(0)）。
 */
@Aspect
@Component
@Order(0)
public class PermissionAspect {

    @Before("@annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            // 过滤器应已拦截；防御性兜底
            throw new BizException(ErrorCode.UNAUTHORIZED); // 40100
        }
        List<String> keys = principal.permissionKeys();
        String required = requirePermission.value();
        if (keys == null || !keys.contains(required)) {
            // 缺菜单权限 → 越权（低权管理员构造 HTTP 直调写端点被拒）
            throw new BizException(ErrorCode.FORBIDDEN); // 40300
        }
    }
}
