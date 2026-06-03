package com.dreamy.identity.it;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.identity.domain.otp.entity.OtpCodeEntity;
import com.dreamy.identity.domain.otp.repository.OtpCodeMapper;
import com.dreamy.identity.domain.otp.service.OtpService;
import com.dreamy.identity.domain.session.entity.UserSessionEntity;
import com.dreamy.identity.domain.session.repository.UserSessionMapper;
import com.dreamy.identity.domain.user.entity.UserEntity;
import com.dreamy.identity.domain.user.entity.UserIdentityEntity;
import com.dreamy.identity.domain.user.model.LoginContext;
import com.dreamy.identity.domain.user.repository.UserIdentityMapper;
import com.dreamy.identity.domain.user.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OtpConcurrencyIT extends AbstractIT {

    @Autowired OtpService otpService;
    @Autowired OtpCodeMapper otpCodeMapper;
    @Autowired UserMapper userMapper;
    @Autowired UserSessionMapper userSessionMapper;
    @Autowired UserIdentityMapper userIdentityMapper;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String EMAIL = "otp-it@dreamy.com";
    private static final String CODE = "123456";

    @BeforeEach
    void setup() {
        // 清理该测试邮箱的所有关联数据（防止跨 IT 残留）
        userSessionMapper.delete(new LambdaQueryWrapper<UserSessionEntity>()
                .inSql(UserSessionEntity::getUserId,
                        "SELECT id FROM `user` WHERE email='" + EMAIL + "'"));
        userIdentityMapper.delete(new LambdaQueryWrapper<UserIdentityEntity>()
                .eq(UserIdentityEntity::getProvider, "email")
                .eq(UserIdentityEntity::getProviderUid, EMAIL));
        userMapper.delete(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, EMAIL));
        otpCodeMapper.delete(new LambdaQueryWrapper<OtpCodeEntity>()
                .eq(OtpCodeEntity::getEmail, EMAIL));

        OtpCodeEntity otp = new OtpCodeEntity();
        otp.setEmail(EMAIL);
        otp.setCodeHash(passwordEncoder.encode(CODE));
        otp.setLength(6);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        otp.setAttempts(0);
        otp.setMaxAttempts(5);
        otp.setStatus("pending");
        otp.setLastSentAt(LocalDateTime.now());
        otp.setVersion(0);
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

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        long consumedCount = otpCodeMapper.selectCount(new LambdaQueryWrapper<OtpCodeEntity>()
                .eq(OtpCodeEntity::getEmail, EMAIL)
                .eq(OtpCodeEntity::getStatus, "consumed"));
        assertThat(consumedCount).isEqualTo(1);
    }
}
