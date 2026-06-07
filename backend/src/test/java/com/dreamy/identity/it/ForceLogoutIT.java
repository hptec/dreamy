package com.dreamy.identity.it;
import com.dreamy.identity.domain.enums.*;

import com.dreamy.identity.domain.user.model.LoginContext;
import com.dreamy.identity.domain.session.service.SessionService;
import com.dreamy.identity.domain.user.service.UserOpsService;
import com.dreamy.identity.infra.SessionValidityCache;
import com.dreamy.identity.infra.SessionValidator;
import com.dreamy.identity.domain.user.entity.User;
import com.dreamy.identity.domain.session.entity.UserSession;
import com.dreamy.identity.domain.user.repository.UserMapper;
import com.dreamy.identity.domain.session.repository.UserSessionMapper;
import com.dreamy.identity.security.TokenPair;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-06：forceLogout/禁用 → Redis 单级失效全集群即时生效（真 MySQL + Redis，零 Mock）。
 * L0 TRACE: FUNC-022, EDGE-023, IT-06
 */
class ForceLogoutIT extends AbstractIT {

    @Autowired SessionService sessionService;
    @Autowired UserOpsService userOpsService;
    @Autowired SessionValidator sessionValidator;
    @Autowired SessionValidityCache validityCache;
    @Autowired UserSessionMapper userSessionMapper;
    @Autowired UserMapper userMapper;

    private Long userId;

    @BeforeEach
    void setup() {
        // 插入最小 user 行（id 由 DB 自增回写）；forceLogout 需要 userId 存在于 user_session
        User user = new User();
        user.setEmail("force-logout-it@dreamy.com");
        user.setEmailVerified(true);
        user.setTier(UserTier.REGULAR);
        user.setStatus(UserStatus.ACTIVE);
        user.setVersion(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        userId = user.getId();
    }

    @Test
    @DisplayName("TC-IT-006 [P0]: forceLogout → session revoked + Redis 键失效 → 后续请求 401（IT-06）")
    void forceLogout_revokesSessionAndRedis() throws Exception {
        // ARRANGE: 建立真实会话（DB + Redis markValid）
        TokenPair tokens = sessionService.openStoreSession(
                userId, "force-logout-it@dreamy.com", AuthProvider.EMAIL, false, LoginContext.empty());
        String tokenId = tokens.getTokenId();

        // 等待 afterCommit 写 Redis（Testcontainers 同步事务，afterCommit 在提交后立即执行）
        Thread.sleep(200);

        // 验证会话有效（Redis 命中）
        assertThat(sessionValidator.isStoreSessionValid(tokenId)).isTrue();

        // ACT: forceLogout 全部会话
        userOpsService.forceLogout(userId, "all", null);

        // 等待 afterCommit DEL Redis
        Thread.sleep(200);

        // ASSERT: DB session.status = revoked
        UserSession session = userSessionMapper.selectOne(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getTokenId, tokenId));
        assertThat(session).isNotNull();
        assertThat(session.getStatus()).isEqualTo("revoked");

        // ASSERT: Redis 有效性键已 DEL → isStoreSessionValid = false
        assertThat(validityCache.isValid(tokenId)).isFalse();
        assertThat(sessionValidator.isStoreSessionValid(tokenId)).isFalse();
    }
}
