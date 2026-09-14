package com.dreamy.infra;

import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.session.repository.AdminSessionMapper;
import com.dreamy.domain.session.repository.UserSessionMapper;

import com.dreamy.infra.grpc.IdentityGateClient;
import com.dreamy.infra.grpc.IdentityUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 请求链路会话有效性校验器(P4 切换版)。
 * 通道开关 IDENTITY_GRPC_ENABLED:
 * - true(切换后):IdentityGate gRPC 调 Rust server(权威主存)——UNAVAILABLE → 503 语义由调用方映射,
 *   绝不返回 401(避免误踢全站登录态);
 * - false(回滚态):直查 DB(原 v1 语义,不变)。
 */
@Component
public class SessionValidator {

    private static final Logger log = LoggerFactory.getLogger(SessionValidator.class);

    private final boolean grpcEnabled;
    private final IdentityGateClient identityGate;
    private final UserSessionMapper userSessionMapper;
    private final AdminSessionMapper adminSessionMapper;
    private final AdminUserMapper adminUserMapper;

    public SessionValidator(
            @Value("${IDENTITY_GRPC_ENABLED:false}") boolean grpcEnabled,
            IdentityGateClient identityGate,
            UserSessionMapper userSessionMapper,
            AdminSessionMapper adminSessionMapper,
            AdminUserMapper adminUserMapper) {
        this.grpcEnabled = grpcEnabled;
        this.identityGate = identityGate;
        this.userSessionMapper = userSessionMapper;
        this.adminSessionMapper = adminSessionMapper;
        this.adminUserMapper = adminUserMapper;
    }

    /** store access token:Rust 主存或 DB 直查(回滚态) */
    public boolean isStoreSessionValid(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        if (grpcEnabled) {
            try {
                return identityGate.validateStoreSession(tokenId);
            } catch (IdentityUnavailableException e) {
                // 基础设施故障 → 抛给过滤器转 503,不伪装成 invalid
                throw e;
            }
        }
        // 回滚态:直查(原 v1)
        return userSessionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dreamy.domain.session.entity.UserSession>()
                        .eq(com.dreamy.domain.session.entity.UserSession::getTokenId, tokenId)
                        .eq(com.dreamy.domain.session.entity.UserSession::getStatus,
                                com.dreamy.enums.SessionStatus.ACTIVE)) > 0;
    }

    /** admin token:Rust 主存或 DB 直查(回滚态) */
    public boolean isAdminSessionValid(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        if (grpcEnabled) {
            try {
                IdentityGateClient.AdminSessionValidity v = identityGate.validateAdminSession(tokenId);
                return v.valid() && v.adminActive();
            } catch (IdentityUnavailableException e) {
                throw e;
            }
        }
        com.dreamy.domain.session.entity.AdminSession session = adminSessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dreamy.domain.session.entity.AdminSession>()
                        .eq(com.dreamy.domain.session.entity.AdminSession::getTokenId, tokenId)
                        .eq(com.dreamy.domain.session.entity.AdminSession::getStatus,
                                com.dreamy.enums.SessionStatus.ACTIVE));
        if (session == null) {
            return false;
        }
        com.dreamy.domain.admin.entity.AdminUser admin = adminUserMapper.selectById(session.getAdminId());
        return admin != null && admin.getStatus() == com.dreamy.enums.AdminStatus.ACTIVE;
    }
}
