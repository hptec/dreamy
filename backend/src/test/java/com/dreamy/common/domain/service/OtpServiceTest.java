package com.dreamy.common.domain.service;
import com.dreamy.enums.*;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.infra.OtpRateLimiter;
import com.dreamy.infra.mail.MailSender;
import com.dreamy.domain.authconfig.entity.AuthConfig;
import com.dreamy.domain.authconfig.service.AuthConfigService;
import com.dreamy.domain.otp.entity.OtpCode;
import com.dreamy.domain.otp.repository.OtpCodeMapper;
import com.dreamy.domain.otp.service.OtpService;
import com.dreamy.domain.otp.service.OtpStateWriter;
import com.dreamy.domain.session.service.SessionService;
import com.dreamy.domain.user.model.LoginContext;
import com.dreamy.domain.user.service.MergeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;
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
    @Mock RedissonClient redissonClient;
    @Mock org.redisson.api.RLock rLock;
    @Mock OtpStateWriter otpStateWriter;

    @InjectMocks OtpService otpService;

    /** 初始化 MyBatis-Plus lambda 缓存（LambdaQueryWrapper/LambdaUpdateWrapper 需要 TableInfo）。 */
    @BeforeAll
    static void initMybatisPlusCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new org.apache.ibatis.session.Configuration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, OtpCode.class);
    }

    @BeforeEach
    void setUp() {
        AuthConfig cfg = new AuthConfig();
        cfg.setOtpLength(6);
        cfg.setOtpTtlMinutes(10);
        cfg.setOtpResendSeconds(60);
        cfg.setOtpMaxAttempts(5);
        cfg.setMinMethods(1);
        when(authConfigService.getConfig()).thenReturn(cfg);
        // DEC-001：consumeValidCode 由 onIdLock("otp:verify", email) 分布式锁包裹；
        // 默认 stub getLock 返回 mock RLock，lock()/unlock() no-op，isLocked() 默认 false。
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @Test
    @DisplayName("TC-UNIT-001: OTP 正确码 → consumed，返回 LoginResult")
    void verifyOtp_correct_returnsLoginResult() {
        OtpCode otp = pendingOtp(3, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.selectOne(any())).thenReturn(otp);
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(mergeService.resolveOrMerge(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(new MergeService.MergeOutcome(activeUser(), false));
        when(sessionService.isNewDevice(any(), any())).thenReturn(false);
        when(sessionService.openStoreSession(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new com.dreamy.security.TokenPair());

        var result = otpService.verifyOtp("test@example.com", "123456",
                LoginContext.empty());

        assertThat(result).isNotNull();
        verify(otpStateWriter, atLeastOnce()).updateStatus(eq(1L), eq(OtpStatus.CONSUMED), any());
    }

    @Test
    @DisplayName("TC-UNIT-002: OTP 错误码未达上限 → 40101 + remaining_attempts")
    void verifyOtp_wrongCode_returnsOtpInvalid() {
        OtpCode otp = pendingOtp(1, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.selectOne(any())).thenReturn(otp);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

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
        OtpCode otp = pendingOtp(4, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.selectOne(any())).thenReturn(otp);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> otpService.verifyOtp("test@example.com", "wrong",
                LoginContext.empty()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_LOCKED));
    }

    @Test
    @DisplayName("TC-UNIT-004: OTP 已过期 → 41001 OTP_EXPIRED")
    void verifyOtp_expired_returnsOtpExpired() {
        OtpCode otp = pendingOtp(0, 5, LocalDateTime.now().minusMinutes(1));
        when(otpCodeMapper.selectOne(any())).thenReturn(otp);

        assertThatThrownBy(() -> otpService.verifyOtp("test@example.com", "123456",
                LoginContext.empty()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    @Test
    @DisplayName("TC-UNIT-005: 无 pending OTP → 41001 OTP_EXPIRED")
    void verifyOtp_noPending_returnsOtpExpired() {
        when(otpCodeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> otpService.verifyOtp("test@example.com", "123456",
                LoginContext.empty()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    // ===== BLOCKER-4: verifyCodeOnly 仅校验码，不归并不开会话 =====

    @Test
    @DisplayName("TC-UNIT-006 [P0]: verifyCodeOnly 正确码 → consumed，不调 resolveOrMerge/openStoreSession（BLOCKER-4）")
    void verifyCodeOnly_correct_consumesWithoutLoginPipeline() {
        OtpCode otp = pendingOtp(0, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.selectOne(any())).thenReturn(otp);
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        otpService.verifyCodeOnly("bind@example.com", "123456");

        // ASSERT: 置 consumed（updateStatus 调用），但绝不触发归并/开会话（否则绑定产生孤立账户）
        verify(otpStateWriter, atLeastOnce()).updateStatus(eq(1L), eq(OtpStatus.CONSUMED), any());
        verify(mergeService, never()).resolveOrMerge(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any(), any());
        verify(sessionService, never()).openStoreSession(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("TC-UNIT-007 [P0]: verifyCodeOnly 错误码 → 40101，仍不开会话（BLOCKER-4）")
    void verifyCodeOnly_wrongCode_throwsAndNoSession() {
        OtpCode otp = pendingOtp(1, 5, LocalDateTime.now().plusMinutes(5));
        when(otpCodeMapper.selectOne(any())).thenReturn(otp);
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
        OtpCode otp = pendingOtp(0, 5, LocalDateTime.now().minusMinutes(1));
        when(otpCodeMapper.selectOne(any())).thenReturn(otp);

        assertThatThrownBy(() -> otpService.verifyCodeOnly("bind@example.com", "123456"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    private OtpCode pendingOtp(int attempts, int maxAttempts, LocalDateTime expiresAt) {
        OtpCode otp = new OtpCode();
        otp.setId(1L);
        otp.setEmail("test@example.com");
        otp.setCodeHash("hash");
        otp.setAttempts(attempts);
        otp.setMaxAttempts(maxAttempts);
        otp.setStatus(OtpStatus.PENDING);
        otp.setExpiresAt(expiresAt);
        otp.setVersion(0);
        return otp;
    }

    private com.dreamy.domain.user.entity.User activeUser() {
        var u = new com.dreamy.domain.user.entity.User();
        u.setId(1L);
        u.setEmail("test@example.com");
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }
}
