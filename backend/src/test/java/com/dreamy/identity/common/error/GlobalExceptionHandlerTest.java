package com.dreamy.identity.common.error;

import com.dreamy.identity.i18n.MessageResolver;
import com.dreamy.identity.i18n.RequestLocaleContext;
import huihao.web.R;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.error.GlobalExceptionHandler;
import com.dreamy.identity.error.InfraException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 单元测试（不依赖 Spring 上下文）。
 * TC-UNIT-060~063。
 * STUB_REASON: 隔离 MessageSource，仅测 handler 映射逻辑。
 * STUB_SCOPE: repository_io（MessageResolver 为 I/O 边界）。
 * L0 TRACE: PATH-01/02/04, EDGE-002/003
 * L2 TRACE: CT-02（错误响应结构）
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock MessageResolver messageResolver;
    @InjectMocks GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        RequestLocaleContext.set(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        RequestLocaleContext.clear();
    }

    @Test
    @DisplayName("TC-UNIT-060 [P0]: BizException → {code,message,details} + 正确 HTTP 状态（PATH-01）")
    void handleBiz_returnsCorrectEnvelope() {
        // ARRANGE
        when(messageResolver.resolve(eq(ErrorCode.OTP_INVALID), any()))
                .thenReturn("Incorrect verification code");
        BizException ex = new BizException(ErrorCode.OTP_INVALID,
                Map.of("remaining_attempts", 3));

        // ACT
        ResponseEntity<R<Object>> resp = handler.handleBiz(ex, null);

        // ASSERT: L1 HTTP 401, L2 code=40101, L3 message 存在, L4 data.remaining_attempts=3
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(40101);
        assertThat(resp.getBody().getMessage()).isEqualTo("Incorrect verification code");
        // R 包络无 details 字段：原 details 装入 data（GlobalExceptionHandler build）
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
        assertThat(data).containsEntry("remaining_attempts", 3);
    }

    @Test
    @DisplayName("TC-UNIT-061 [P0]: InfraException → 5xx + {code,message,null details}（PATH-02 不泄漏堆栈）")
    void handleInfra_returns5xxWithoutDetails() {
        // ARRANGE
        when(messageResolver.resolve(eq(ErrorCode.DATABASE_ERROR), any()))
                .thenReturn("Data operation failed");
        InfraException ex = new InfraException(ErrorCode.DATABASE_ERROR);

        // ACT
        ResponseEntity<R<Object>> resp = handler.handleInfra(ex, null);

        // ASSERT: L1 HTTP 500, L2 code=50001, L3 data=null（不泄漏）
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody().getCode()).isEqualTo(50001);
        assertThat(resp.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("TC-UNIT-062 [P0]: 未预期 Exception → 50000 INTERNAL_ERROR（PATH-04 兜底）")
    void handleUnexpected_returns50000() {
        // ARRANGE
        when(messageResolver.resolve(eq(ErrorCode.INTERNAL_ERROR), any()))
                .thenReturn("Something went wrong");

        // ACT
        ResponseEntity<R<Object>> resp = handler.handleUnexpected(new RuntimeException("boom"), null);

        // ASSERT: L1 HTTP 500, L2 code=50000, L3 data=null
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody().getCode()).isEqualTo(50000);
        assertThat(resp.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("TC-UNIT-063 [P0]: 25 错误码 HTTP 状态映射正确（CT-02 code↔HTTP）")
    void errorCode_httpStatusMapping_correct() {
        // ASSERT: L4 关键码的 HTTP 状态精确校验
        assertThat(ErrorCode.VALIDATION_ERROR.getHttpStatus()).isEqualTo(422);
        assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus()).isEqualTo(401);
        assertThat(ErrorCode.OTP_INVALID.getHttpStatus()).isEqualTo(401);
        assertThat(ErrorCode.REFRESH_INVALID.getHttpStatus()).isEqualTo(401);
        assertThat(ErrorCode.FORBIDDEN.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.ACCOUNT_DISABLED.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.SUPER_ADMIN_PROTECTED.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.NOT_FOUND.getHttpStatus()).isEqualTo(404);
        assertThat(ErrorCode.EMAIL_EXISTS.getHttpStatus()).isEqualTo(409);
        assertThat(ErrorCode.OTP_EXPIRED.getHttpStatus()).isEqualTo(410);
        assertThat(ErrorCode.OTP_LOCKED.getHttpStatus()).isEqualTo(410);
        assertThat(ErrorCode.RESEND_TOO_SOON.getHttpStatus()).isEqualTo(429);
        assertThat(ErrorCode.RATE_LIMITED.getHttpStatus()).isEqualTo(429);
        assertThat(ErrorCode.INTERNAL_ERROR.getHttpStatus()).isEqualTo(500);
        assertThat(ErrorCode.OIDC_UNAVAILABLE.getHttpStatus()).isEqualTo(502);
        assertThat(ErrorCode.OIDC_TIMEOUT.getHttpStatus()).isEqualTo(504);
    }
}
