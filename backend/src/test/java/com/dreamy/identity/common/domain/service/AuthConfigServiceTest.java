package com.dreamy.identity.common.domain.service;

import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.domain.authconfig.entity.AuthConfigEntity;
import com.dreamy.identity.domain.authconfig.repository.AuthConfigMapper;
import com.dreamy.identity.domain.authconfig.service.AuthConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UT-06 AuthConfig 区间校验（CV-002）。
 * 约束: ttl/resend/attempts/min_methods/length 越界 → 40002 CONFIG_OUT_OF_RANGE（EDGE-019）；P0。
 */
@ExtendWith(MockitoExtension.class)
class AuthConfigServiceTest {

    @Mock AuthConfigMapper authConfigMapper;
    @InjectMocks AuthConfigService authConfigService;

    @Test
    @DisplayName("TC-UNIT-040: otp_length=5 越界 → 40002")
    void validateRange_invalidLength_throws40002() {
        assertThatThrownBy(() -> authConfigService.validateRange(cfg(5, 10, 60, 5, 1)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONFIG_OUT_OF_RANGE));
    }

    @Test
    @DisplayName("TC-UNIT-041: otp_ttl_minutes=0 越界 → 40002")
    void validateRange_ttlZero_throws40002() {
        assertThatThrownBy(() -> authConfigService.validateRange(cfg(6, 0, 60, 5, 1)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONFIG_OUT_OF_RANGE));
    }

    @Test
    @DisplayName("TC-UNIT-042: otp_resend_seconds=5 越界 → 40002")
    void validateRange_resendTooLow_throws40002() {
        assertThatThrownBy(() -> authConfigService.validateRange(cfg(6, 10, 5, 5, 1)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONFIG_OUT_OF_RANGE));
    }

    @Test
    @DisplayName("TC-UNIT-043: 合法配置 → 不抛异常")
    void validateRange_valid_noException() {
        authConfigService.validateRange(cfg(6, 10, 60, 5, 1));
    }

    private AuthConfigEntity cfg(int length, int ttl, int resend, int maxAttempts, int minMethods) {
        AuthConfigEntity c = new AuthConfigEntity();
        c.setOtpLength(length);
        c.setOtpTtlMinutes(ttl);
        c.setOtpResendSeconds(resend);
        c.setOtpMaxAttempts(maxAttempts);
        c.setMinMethods(minMethods);
        return c;
    }
}
