package com.dreamy.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 服务端 RBAC 权限要求注解（BLOCKER-2）。
 * 约束: api-detail §0「RBAC（admin）：路由→permission_key 映射，缺权限→403 40300」；
 * BE-DIM-6 服务端强制鉴权（不依赖前端菜单守卫）；decision 决策7 RBAC。
 *
 * 标注在 admin 写端点方法上，PermissionAspect 校验当前管理员
 * AuthContext.get().permissionKeys() 是否包含 value() 指定的 menu key，
 * 缺失则抛 BizException(FORBIDDEN/40300)。超管（permission_keys 含全部 22 项）自然通过。
 *
 * 映射关系（菜单 permission key）：
 *   - admins CRUD     → /system/admins
 *   - roles           → /system/roles
 *   - auth-config     → /system/auth
 *   - operation-logs  → /system/logs
 *   - users           → /customers
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 需要的菜单 permission key（如 "/system/admins"） */
    String value();
}
