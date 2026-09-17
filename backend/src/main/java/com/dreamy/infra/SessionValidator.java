package com.dreamy.infra;

import com.dreamy.infra.grpc.IdentityGateClient;
import org.springframework.stereotype.Component;

/**
 * 请求链路会话有效性校验器(纯 gRPC 终态;2026-09-17 Java 删码:回滚态 DB 分支已移除)。
 * IdentityGate gRPC 调 Rust server(权威主存)——UNAVAILABLE → 503 语义由调用方映射,
 * 绝不返回 401(避免误踢全站登录态)。
 */
@Component
public class SessionValidator {

    private final IdentityGateClient identityGate;

    public SessionValidator(IdentityGateClient identityGate) {
        this.identityGate = identityGate;
    }

    /** store access token:Rust 主存校验 */
    public boolean isStoreSessionValid(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        return identityGate.validateStoreSession(tokenId);
    }

    /** admin token:Rust 主存校验(含管理员状态复核) */
    public boolean isAdminSessionValid(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        IdentityGateClient.AdminSessionValidity v = identityGate.validateAdminSession(tokenId);
        return v.valid() && v.adminActive();
    }
}
