package com.dreamy.aspect;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.infra.grpc.IdentityGateClient;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务端 RBAC 校验切面(纯 gRPC 终态;2026-09-17 Java 删码:回滚态 DB 分支已移除)。
 * IdentityGate gRPC 实时权限解析(变更下一请求生效语义不变)。
 */
@Aspect
@Component
@Order(0)
public class PermissionAspect {

    private final IdentityGateClient identityGate;

    public PermissionAspect(IdentityGateClient identityGate) {
        this.identityGate = identityGate;
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
        return identityGate.resolvePermissions(Long.parseLong(adminId));
    }
}
