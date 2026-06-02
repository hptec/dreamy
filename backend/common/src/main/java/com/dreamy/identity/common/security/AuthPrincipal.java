package com.dreamy.identity.common.security;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JWT 解析后的鉴权主体。store/admin 共用结构，按 type 区分。
 * 约束: shared-contracts jwt_isolation claims（store: sub/jti/method/typ=store；admin: sub/jti/role_id/permission_keys/typ=admin）。
 */
public record AuthPrincipal(
        String subject,          // store=user_id / admin=admin_id
        String tokenId,          // jti
        String type,             // store | admin
        String method,           // store 登录方式 email/google/apple（admin 为 null）
        @JsonProperty("refresh") boolean refresh,  // 是否 refresh token
        String roleId,           // admin only
        List<String> permissionKeys  // admin only
) {
    public static final String TYPE_STORE = "store";
    public static final String TYPE_ADMIN = "admin";
}
