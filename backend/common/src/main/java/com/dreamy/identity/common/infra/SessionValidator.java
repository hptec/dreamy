package com.dreamy.identity.common.infra;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.identity.common.repository.entity.AdminSessionEntity;
import com.dreamy.identity.common.repository.entity.UserSessionEntity;
import com.dreamy.identity.common.repository.mapper.AdminSessionMapper;
import com.dreamy.identity.common.repository.mapper.UserSessionMapper;
import org.springframework.stereotype.Component;

/**
 * 请求链路会话有效性校验器（BLOCKER-1）。
 * 约束: api-detail §0「会话有效性」——store 校验 redis store:session:valid:{jti} 存在且 DB session.status=active；
 * admin 校验 admin_session.status=active。EDGE-023 即时失效；DG-003 Redis 未命中/不可用降级查 DB。
 *
 * 校验语义（两端一致）：
 * 1. Redis 命中（TTL30s 内）→ 视为有效，避免每请求打 DB（QP-003 优先读 Redis）。
 * 2. Redis 未命中/不可用（cache.isValid=false）→ 降级查 DB session.status='active'：
 *    - DB 命中 active → 有效，并回填 Redis（自愈缓存，减少后续 DB 压力）。
 *    - DB 无 active 记录（已 revoked/不存在）→ 无效，调用方返回 401。
 */
@Component
public class SessionValidator {

    private final SessionValidityCache storeCache;
    private final AdminSessionValidityCache adminCache;
    private final UserSessionMapper userSessionMapper;
    private final AdminSessionMapper adminSessionMapper;

    public SessionValidator(SessionValidityCache storeCache,
                            AdminSessionValidityCache adminCache,
                            UserSessionMapper userSessionMapper,
                            AdminSessionMapper adminSessionMapper) {
        this.storeCache = storeCache;
        this.adminCache = adminCache;
        this.userSessionMapper = userSessionMapper;
        this.adminSessionMapper = adminSessionMapper;
    }

    /** store access token：Redis 命中即有效；否则降级查 user_session.status='active'（DG-003） */
    public boolean isStoreSessionValid(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        if (storeCache.isValid(tokenId)) {
            return true;
        }
        LambdaQueryWrapper<UserSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSessionEntity::getTokenId, tokenId)
                .eq(UserSessionEntity::getStatus, "active");
        boolean dbActive = userSessionMapper.selectCount(qw) > 0;
        if (dbActive) {
            // 自愈：回填 Redis 单级键，降低后续请求 DB 压力
            storeCache.markValid(tokenId);
        }
        return dbActive;
    }

    /** admin token：Redis 命中即有效；否则降级查 admin_session.status='active'（DG-003） */
    public boolean isAdminSessionValid(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        if (adminCache.isValid(tokenId)) {
            return true;
        }
        LambdaQueryWrapper<AdminSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSessionEntity::getTokenId, tokenId)
                .eq(AdminSessionEntity::getStatus, "active");
        boolean dbActive = adminSessionMapper.selectCount(qw) > 0;
        if (dbActive) {
            adminCache.markValid(tokenId);
        }
        return dbActive;
    }
}
