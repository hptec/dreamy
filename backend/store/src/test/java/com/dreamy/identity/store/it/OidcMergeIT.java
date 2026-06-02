package com.dreamy.identity.store.it;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IT 骨架：OIDC 归并单事务原子性（IT-02）+ 重复回调幂等（IT-03）。
 * 需要 MySQL（Testcontainers）。
 * STUB_SCOPE: none（集成测试严格零 Mock）。
 * L0 TRACE: FUNC-004/005, TX-002, EDGE-016/017
 * L2 TRACE: IT-02, IT-03
 */
@Disabled("requires Docker - run in CI with testcontainers")
class OidcMergeIT {

    @Test
    @DisplayName("TC-IT-002 [P0]: oidcCallback 归并单事务 — identity 挂载 + operation_log 原子（IT-02）")
    void oidcCallback_merge_atomicTransaction() {
        // ARRANGE: 真实 MySQL；已有 email user；OIDC 同邮箱 email_verified=true
        // ACT: identityService.oidcLogin(...)
        // ASSERT: L5 user_identity 行存在 + operation_log 行存在（同事务）
        //         L5 若事务回滚则两者均不存在
        throw new UnsupportedOperationException("IT skeleton - requires Docker");
    }

    @Test
    @DisplayName("TC-IT-003 [P0]: 重复 OIDC 回调幂等 — 唯一索引命中返回既有 user，不重复建号（IT-03）")
    void oidcCallback_duplicate_idempotent() {
        // ARRANGE: 真实 MySQL；第一次 oidcLogin 已建 user_identity
        // ACT: 相同 provider+provider_uid 再次 oidcLogin
        // ASSERT: L5 user_identity count = 1（不重复）；返回同一 user.id
        throw new UnsupportedOperationException("IT skeleton - requires Docker");
    }
}
