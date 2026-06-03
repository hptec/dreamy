package com.dreamy.identity.common.domain.service;

import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.infra.OtpRateLimiter;
import com.dreamy.identity.infra.mail.MailSender;
import com.dreamy.identity.domain.authconfig.entity.AuthConfigEntity;
import com.dreamy.identity.domain.authconfig.service.AuthConfigService;
import com.dreamy.identity.domain.otp.entity.OtpCodeEntity;
import com.dreamy.identity.domain.otp.repository.OtpCodeMapper;
import com.dreamy.identity.domain.otp.service.OtpService;
import com.dreamy.identity.domain.session.service.SessionService;
import com.dreamy.identity.domain.user.model.LoginContext;
import com.dreamy.identity.domain.user.service.MergeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UT-01 OtpCode 校验单元测试（领域不变量）。
 * 约束: UT-01（正确/错误 attempts+1/达上限 locked/过期）；P0。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock OtpCodeMapper otpCodeMapper;
    @Mock AuthConfigService authConfigService;
    @Mock OtpRateLimiter rateLimiter;
    @Mock MailSender mailSender;
    @Mock MergeService mergeService;
    @Mock SessionService sessionService;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks OtpService otpService;

    @BeforeEach
    void setUp() {
        AuthConfigEntity cfg = new AuthConfigEntity();
        cfg.setOtpLength(6);
        cfg.setOtpTtlMinutes(10);
        cfg.setOtpResendSeconds(60);
        cfg.setOtpMaxAttempts(5);
        cfg.setMinMethods(1);
        when(authConfigService.getConfig()).thenReturn(cfg);
        when(otpCodeMapper.update(any(), any())).thenReturn(1);
        when(otpCodeMapper.updateById(any(OtpCodeEntity.class))).thenReturn(1);
    }

    @Test
    @DisplayName("TC-UNIT-001: OTP 正确码 → consumed，返回 LoginResult")
    void verifyOtp_correct_returnsLoginResult() {
        OtpCodeEntity otp = pendingOtp(3, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.lockPendingByEmail("test@example.com")).thenReturn(otp);
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(mergeService.resolveOrMerge(any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new MergeService.MergeOutcome(activeUser(), false));
        when(sessionService.isNewDevice(any(), any())).thenReturn(false);
        when(sessionService.openStoreSession(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new com.dreamy.identity.security.TokenPair());

        var result = otpService.verifyOtp("test@example.com", "123456",
                LoginContext.empty());

        assertThat(result).isNotNull();
        verify(otpCodeMapper, atLeastOnce()).update(any(), any());
    }

    @Test
    @DisplayName("TC-UNIT-002: OTP 错误码未达上限 → 40101 + remaining_attempts")
    void verifyOtp_wrongCode_returnsOtpInvalid() {
        OtpCodeEntity otp = pendingOtp(1, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.lockPendingByEmail("test@example.com")).thenReturn(otp);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(otpCodeMapper.selectById(any())).thenReturn(otp);

        assertThatThrownBy(() -> otpService.verifyOtp("test@example.com", "wrong",
                LoginContext.empty()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID);
                    assertThat(biz.getDetails()).containsKey("remaining_attempts");
                });
    }

    @Test
    @DisplayName("TC-UNIT-003: OTP 错误达上限 → 41002 OTP_LOCKED")
    void verifyOtp_maxAttempts_locked() {
        OtpCodeEntity otp = pendingOtp(4, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.lockPendingByEmail("test@example.com")).thenReturn(otp);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> otpService.verifyOtp("test@example.com", "wrong",
                LoginContext.empty()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_LOCKED));
    }

    @Test
    @DisplayName("TC-UNIT-004: OTP 已过期 → 41001 OTP_EXPIRED")
    void verifyOtp_expired_returnsOtpExpired() {
        OtpCodeEntity otp = pendingOtp(0, 5, LocalDateTime.now().minusMinutes(1));
        when(otpCodeMapper.lockPendingByEmail("test@example.com")).thenReturn(otp);

        assertThatThrownBy(() -> otpService.verifyOtp("test@example.com", "123456",
                LoginContext.empty()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    @Test
    @DisplayName("TC-UNIT-005: 无 pending OTP → 41001 OTP_EXPIRED")
    void verifyOtp_noPending_returnsOtpExpired() {
        when(otpCodeMapper.lockPendingByEmail(any())).thenReturn(null);

        assertThatThrownBy(() -> otpService.verifyOtp("test@example.com", "123456",
                LoginContext.empty()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    // ===== BLOCKER-4: verifyCodeOnly 仅校验码，不归并不开会话 =====

    @Test
    @DisplayName("TC-UNIT-006 [P0]: verifyCodeOnly 正确码 → consumed，不调 resolveOrMerge/openStoreSession（BLOCKER-4）")
    void verifyCodeOnly_correct_consumesWithoutLoginPipeline() {
        OtpCodeEntity otp = pendingOtp(0, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.lockPendingByEmail("bind@example.com")).thenReturn(otp);
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        otpService.verifyCodeOnly("bind@example.com", "123456");

        // ASSERT: 置 consumed（update 调用），但绝不触发归并/开会话（否则绑定产生孤立账户）
        verify(otpCodeMapper, atLeastOnce()).update(any(), any());
        verify(mergeService, never()).resolveOrMerge(any(), any(), any(), anyBoolean(), anyBoolean(), any());
        verify(sessionService, never()).openStoreSession(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("TC-UNIT-007 [P0]: verifyCodeOnly 错误码 → 40101，仍不开会话（BLOCKER-4）")
    void verifyCodeOnly_wrongCode_throwsAndNoSession() {
        OtpCodeEntity otp = pendingOtp(1, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.lockPendingByEmail("bind@example.com")).thenReturn(otp);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(otpCodeMapper.selectById(any())).thenReturn(otp);

        assertThatThrownBy(() -> otpService.verifyCodeOnly("bind@example.com", "wrong"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID));

        verify(sessionService, never()).openStoreSession(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("TC-UNIT-008 [P0]: verifyCodeOnly 过期码 → 41001 OTP_EXPIRED（BLOCKER-4）")
    void verifyCodeOnly_expired_throwsOtpExpired() {
        OtpCodeEntity otp = pendingOtp(0, 5, LocalDateTime.now().minusMinutes(1));
        when(otpCodeMapper.lockPendingByEmail("bind@example.com")).thenReturn(otp);

        assertThatThrownBy(() -> otpService.verifyCodeOnly("bind@example.com", "123456"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    private OtpCodeEntity pendingOtp(int attempts, int maxAttempts, LocalDateTime expiresAt) {
        OtpCodeEntity otp = new OtpCodeEntity();
        otp.setId(1L);
        otp.setEmail("test@example.com");
        otp.setCodeHash("hash");
        otp.setAttempts(attempts);
        otp.setMaxAttempts(maxAttempts);
        otp.setStatus("pending");
        otp.setExpiresAt(expiresAt);
        otp.setVersion(0);
        return otp;
    }

    private com.dreamy.identity.domain.user.entity.UserEntity activeUser() {
        var u = new com.dreamy.identity.domain.user.entity.UserEntity();
        u.setId(1L);
        u.setEmail("test@example.com");
        u.setStatus("active");
        return u;
    }
}
