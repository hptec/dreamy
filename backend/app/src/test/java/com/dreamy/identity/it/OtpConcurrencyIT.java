package com.dreamy.identity.it;

import com.dreamy.identity.common.domain.model.LoginContext;
import com.dreamy.identity.common.domain.service.OtpService;
import com.dreamy.identity.common.domain.service.SessionService;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.infra.SessionValidityCache;
import com.dreamy.identity.common.repository.entity.OtpCodeEntity;
import com.dreamy.identity.common.repository.mapper.OtpCodeMapper;
import com.dreamy.identity.common.repository.mapper.UserSessionMapper;
import com.dreamy.identity.common.repository.entity.UserSessionEntity;
import com.dreamy.identity.common.security.TokenPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-01：OTP 并发行锁防绕过（真 MySQL + Redis，零 Mock）。
 * L0 TRACE: FUNC-002, TX-001
 */
class OtpConcurrencyIT extends AbstractIT {

    @Autowired OtpService otpService;
    @Autowired OtpCodeMapper otpCodeMapper;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String EMAIL = "otp-it@dreamy.com";
    private static final String CODE = "123456";

    @BeforeEach
    void insertOtp() {
        // 清理旧数据
        otpCodeMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OtpCodeEntity>()
                .eq(OtpCodeEntity::getEmail, EMAIL));
        // 插入一条 pending OTP
        OtpCodeEntity otp = new OtpCodeEntity();
        otp.setId(UUID.randomUUID().toString());
        otp.setEmail(EMAIL);
        otp.setCodeHash(passwordEncoder.encode(CODE));
        otp.setLength(6);
        otp.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        otp.setAttempts(0);
        otp.setMaxAttempts(5);
        otp.setStatus("pending");
        otp.setLastSentAt(OffsetDateTime.now(ZoneOffset.UTC));
        otp.setVersion(0);
        otp.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        otpCodeMapper.insert(otp);
    }

    @Test
    @DisplayName("TC-IT-001 [P0]: 并发两请求同码 → 行锁串行，仅一个成功，另一个 40101/41002（IT-01）")
    void verifyOtp_concurrent_onlyOneSucceeds() throws Exception {
        LoginContext ctx = LoginContext.empty();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            try {
                otpService.verifyOtp(EMAIL, CODE, ctx);
                successCount.incrementAndGet();
            } catch (Exception e) {
                // 失败可能是 BizException（行锁串行后读到 consumed→OTP_EXPIRED/INVALID）
                // 或 DeadlockLoserDataAccessException（InnoDB 死锁回滚一方）——
                // 两者都是 TX-001 防并发绕过的合法失败形式：关键是不会两个都成功。
                failCount.incrementAndGet();
            }
        });
        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            try {
                otpService.verifyOtp(EMAIL, CODE, ctx);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        });
        CompletableFuture.allOf(t1, t2).get();

        // TX-001 核心安全保证：恰好一个成功，绝不会两个都成功（防并发绕过）
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // DB 回查：该 email 不再有 pending（已被消费/失效），且不存在两条 consumed
        long consumedCount = otpCodeMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OtpCodeEntity>()
                        .eq(OtpCodeEntity::getEmail, EMAIL)
                        .eq(OtpCodeEntity::getStatus, "consumed"));
        assertThat(consumedCount).isEqualTo(1);
    }
}
