package com.dreamy.identity.store.it;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IT 骨架：OTP 并发行锁防绕过（IT-01）。
 * 需要 MySQL + Redis（Testcontainers）。
 * STUB_SCOPE: none（集成测试严格零 Mock）。
 * L0 TRACE: FUNC-002, TX-001
 * L2 TRACE: IT-01
 */
@Disabled("requires Docker - run in CI with testcontainers")
class OtpConcurrencyIT {

    @Test
    @DisplayName("TC-IT-001 [P0]: 并发两请求同码 → 行锁串行，仅一个成功，另一个 40101/41002（IT-01）")
    void verifyOtp_concurrent_onlyOneSucceeds() {
        // ARRANGE: 真实 MySQL + Redis；INSERT pending OTP；两线程同时 verifyOtp
        // ACT: CompletableFuture.allOf(thread1, thread2)
        // ASSERT: L5 exactly one LoginResult + one BizException(OTP_INVALID or OTP_LOCKED)
        //         L5 otp.status = consumed（DB 回查）
        throw new UnsupportedOperationException("IT skeleton - requires Docker");
    }
}
