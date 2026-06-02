package com.dreamy.identity.admin.it;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IT 骨架：forceLogout/禁用 → Redis 单级失效全集群即时生效（IT-06）。
 * 需要 MySQL + Redis（Testcontainers）。
 * STUB_SCOPE: none（集成测试严格零 Mock）。
 * L0 TRACE: FUNC-022, EDGE-023, IT-06
 * L2 TRACE: IT-06
 */
@Disabled("requires Docker - run in CI with testcontainers")
class ForceLogoutIT {

    @Test
    @DisplayName("TC-IT-006 [P0]: forceLogout → session revoked + Redis 键失效 → 后续请求 401（IT-06）")
    void forceLogout_revokesSessionAndRedis() {
        // ARRANGE: 真实 MySQL + Redis；openStoreSession 建立会话，Redis markValid
        // ACT: userOpsService.forceLogout(userId, "all", null)
        // ASSERT: L5 user_session.status = revoked（DB 回查）
        //         L5 Redis key 不存在（validityCache.isValid = false）
        //         L5 后续 parseStoreToken + validityCache 校验 → 401
        throw new UnsupportedOperationException("IT skeleton - requires Docker");
    }
}
