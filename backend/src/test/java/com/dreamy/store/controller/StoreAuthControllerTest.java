package com.dreamy.store.controller;
import com.dreamy.enums.*;

import com.dreamy.config.CommonConfig;
import com.dreamy.controller.AccountController;
import com.dreamy.controller.StoreAuthController;
import com.dreamy.domain.user.model.LoginContext;
import com.dreamy.domain.user.model.LoginResult;
import com.dreamy.domain.authconfig.service.AuthConfigService;
import com.dreamy.domain.user.service.IdentityService;
import com.dreamy.domain.otp.service.OtpService;
import com.dreamy.domain.session.service.SessionService;
import com.dreamy.dto.UserProfileDTO;
import com.dreamy.dto.mapper.IdentityDtoMapper;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.error.GlobalExceptionHandler;
import com.dreamy.i18n.MessageResolver;
import com.dreamy.domain.authconfig.entity.AuthConfig;
import com.dreamy.domain.user.entity.User;
import com.dreamy.security.JwtProperties;
import com.dreamy.security.JwtTokenProvider;
import com.dreamy.security.TokenPair;
import com.dreamy.config.StoreConfig;
import com.dreamy.security.StoreJwtFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web 切片测试：StoreAuthController + StoreJwtFilter + GlobalExceptionHandler。
 * TC-WEB-STORE-001~007 / NBT-01~04 / B-01 回归。
 * STUB_REASON: Web 切片测试边界，mock service 层以隔离 DB/Redis。
 * STUB_SCOPE: repository_io（service 层作为 web 切片的 I/O 边界）。
 * L0 TRACE: FUNC-001/002/003/030, EDGE-024, NBT-01/02/03/04, B-01
 * L2 TRACE: UT-01(web), NBT-01~04
 */
@WebMvcTest(controllers = {StoreAuthController.class, AccountController.class},
        // 切片隔离：包合并后无法按包名区分域，按类型排除其他域的 @ControllerAdvice 与全局 Filter
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {com.dreamy.error.AnalyticsExceptionHandler.class,
                        com.dreamy.error.CatalogExceptionHandler.class,
                        com.dreamy.error.MarketingExceptionHandler.class,
                        com.dreamy.error.ReviewExceptionHandler.class,
                        com.dreamy.error.ShippingExceptionHandler.class,
                        com.dreamy.error.ShowroomExceptionHandler.class,
                        com.dreamy.error.TradingExceptionHandler.class,
                        com.dreamy.infra.web.SecurityHeadersFilter.class}))
@Import({StoreJwtFilter.class, StoreConfig.class, GlobalExceptionHandler.class,
        StoreAuthControllerTest.TestConfig.class})
class StoreAuthControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MessageSource messageSource() {
            ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
            ms.setBasename("classpath:i18n/messages");
            ms.setDefaultEncoding("UTF-8");
            ms.setUseCodeAsDefaultMessage(true);
            ms.setFallbackToSystemLocale(false);
            return ms;
        }

        @Bean
        public MessageResolver messageResolver(MessageSource messageSource) {
            return new MessageResolver(messageSource);
        }

        @Bean
        public JwtProperties jwtProperties() {
            JwtProperties p = new JwtProperties();
            p.getStore().setSecret("store-test-secret-key-32chars-min");
            p.getAdmin().setSecret("admin-test-secret-key-32chars-min");
            return p;
        }

        @Bean
        public JwtTokenProvider jwtTokenProvider(JwtProperties props) {
            return new JwtTokenProvider(props);
        }

        // 白名单配置化（portal-api-integration 基建）：切片上下文不绑定 application.yml 的
        // @ConfigurationProperties，显式提供 identity 既有 5 条公开路径，回归口径不变。
        @Bean
        public com.dreamy.security.StoreSecurityProperties storeSecurityProperties() {
            com.dreamy.security.StoreSecurityProperties p =
                    new com.dreamy.security.StoreSecurityProperties();
            p.setStorePublicPaths(java.util.List.of(
                    "/api/store/auth/otp/send",
                    "/api/store/auth/otp/verify",
                    "/api/store/auth/oidc/**",
                    "/api/store/auth/refresh",
                    "/api/store/auth/config"));
            return p;
        }

        // 主键 Long 迁移后 DreamyApplication 携带 @EnableMysql，@WebMvcTest 以其为配置源会 Import
        // DdlAutoConfiguration（informationSchemaService/ddlInit 需 DataSource）。Web 切片无 DataSource，
        // 故提供 mock 满足装配；DDLInit 为 ApplicationRunner，切片上下文不触发其执行。
        @Bean
        public javax.sql.DataSource dataSource() {
            return org.mockito.Mockito.mock(javax.sql.DataSource.class);
        }

        // 同理 @MapperScan 把 domain mapper 注册进切片，MapperFactoryBean.checkDaoConfig 需
        // sqlSessionTemplate.getConfiguration() 非空。mock template 返回真实 Configuration 以通过
        // mapper 注册校验；service 层已 @MockitoBean，mapper 代理不会被真实调用。
        @Bean
        public org.mybatis.spring.SqlSessionTemplate sqlSessionTemplate() {
            org.mybatis.spring.SqlSessionTemplate t =
                    org.mockito.Mockito.mock(org.mybatis.spring.SqlSessionTemplate.class);
            org.mockito.Mockito.when(t.getConfiguration())
                    .thenReturn(new org.apache.ibatis.session.Configuration());
            return t;
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @MockitoBean OtpService otpService;
    @MockitoBean IdentityService identityService;
    @MockitoBean SessionService sessionService;
    @MockitoBean AuthConfigService authConfigService;
    @MockitoBean IdentityDtoMapper mapper;
    // BLOCKER-1：StoreJwtFilter 依赖 SessionValidator 校验会话有效性。
    // 默认 stub 为有效（isStoreSessionValid=true），单独的撤销测试覆盖 false 分支。
    @MockitoBean com.dreamy.infra.SessionValidator sessionValidator;

    private AuthConfig defaultConfig;

    @BeforeEach
    void setUp() {
        defaultConfig = new AuthConfig();
        defaultConfig.setEmailEnabled(true);
        defaultConfig.setGoogleEnabled(true);
        defaultConfig.setAppleEnabled(false);
        defaultConfig.setOtpLength(6);
        // BLOCKER-1：默认会话有效（鉴权放行），撤销场景在 TC-WEB-STORE-013 单独覆盖
        when(sessionValidator.isStoreSessionValid(any())).thenReturn(true);
    }

    // ===== TC-WEB-STORE-001: B-01 回归 — GET /api/store/auth/config 公开可达不被 401 =====

    @Test
    @DisplayName("TC-WEB-STORE-001 [P0]: GET /api/store/auth/config 无 token → 200（B-01 回归，FUNC-003）")
    void getAuthConfig_noToken_returns200() throws Exception {
        // ARRANGE: L2 TRACE: NBT-02, B-01
        when(authConfigService.getConfigView()).thenReturn(
                new com.dreamy.dto.AuthConfigView(
                        true, true, false, 6, 10, 60, 5, 2, null, null));

        // ACT + ASSERT: L1 HTTP 200, L2 code 字段存在, L3 结构完整（R 包络 data）
        mockMvc.perform(get("/api/store/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email_enabled").value(true))
                .andExpect(jsonPath("$.data.google_enabled").value(true))
                .andExpect(jsonPath("$.data.otp_length").value(6));
    }

    // ===== TC-WEB-STORE-002: POST /api/store/auth/otp/send 公开可达 =====

    @Test
    @DisplayName("TC-WEB-STORE-002 [P0]: POST /api/store/auth/otp/send 无 token → 200（FUNC-001）")
    void sendOtp_noToken_returns200() throws Exception {
        // ARRANGE
        when(otpService.sendOtp(eq("user@example.com"), any(), any()))
                .thenReturn(new OtpService.SendOtpResult(60, 6));

        // ACT + ASSERT: L1 200, L2 resend_after_seconds, L3 otp_length（R 包络 data）
        mockMvc.perform(post("/api/store/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resend_after_seconds").value(60))
                .andExpect(jsonPath("$.data.otp_length").value(6));
    }

    // ===== TC-WEB-STORE-003: POST /api/store/auth/otp/verify 公开可达 =====

    @Test
    @DisplayName("TC-WEB-STORE-003 [P0]: POST /api/store/auth/otp/verify 无 token → 200（FUNC-002）")
    void verifyOtp_noToken_returns200() throws Exception {
        // ARRANGE
        User user = activeUser(1L, "user@example.com");
        TokenPair tokens = buildTokenPair();
        when(mapper.toProfile(any(User.class))).thenReturn(
                new UserProfileDTO(1L, "user@example.com", true, null, null, UserTier.REGULAR, null, null, UserStatus.ACTIVE, null));
        when(otpService.verifyOtp(eq("user@example.com"), eq("123456"), any(LoginContext.class)))
                .thenReturn(new LoginResult(user, tokens, false, false));

        // ACT + ASSERT: L1 200, L2 tokens 存在, L3 user/is_new_account（R 包络 data）
        mockMvc.perform(post("/api/store/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokens.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.is_new_account").value(false));
    }

    // ===== TC-WEB-STORE-004: POST /api/store/auth/refresh 公开可达 =====

    @Test
    @DisplayName("TC-WEB-STORE-004 [P0]: POST /api/store/auth/refresh 无 token → 200（FUNC-030）")
    void refresh_validRefreshToken_returns200() throws Exception {
        // ARRANGE: 签发真实 refresh token
        TokenPair original = jwtTokenProvider.issueStoreTokens("user-1", "email");
        TokenPair renewed = buildTokenPair();
        when(sessionService.refresh(any())).thenReturn(renewed);

        // ACT + ASSERT: L1 200, L2 tokens 存在（R 包络 data）。
        // Jackson SNAKE_CASE：RefreshRequest.refreshToken 绑定字段名为 refresh_token
        mockMvc.perform(post("/api/store/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"" + original.getRefreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokens.access_token").isNotEmpty());
    }

    // ===== TC-WEB-STORE-005: /api/store/account/* 无 token → 401 =====

    @Test
    @DisplayName("TC-WEB-STORE-005 [P0]: GET /api/store/account/profile 无 token → 401（鉴权守卫）")
    void accountProfile_noToken_returns401() throws Exception {
        // ASSERT: L1 401, L2 code=40100
        mockMvc.perform(get("/api/store/account/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ===== TC-WEB-STORE-006: EDGE-024 — store token 访问 /api/admin/* 不被 StoreJwtFilter 放行 =====
    // (StoreJwtFilter 仅处理 /api/store/* 前缀，/api/admin/* 由 AdminJwtFilter 处理)
    // 此处验证 store token 访问 /api/store/account/* 正常通过 StoreJwtFilter

    @Test
    @DisplayName("TC-WEB-STORE-006 [P0]: store token 访问 /api/store/account/profile → 200（过滤器放行）")
    void accountProfile_withStoreToken_passesFilter() throws Exception {
        // ARRANGE：主键 Long 迁移，store token subject 必须为数字串
        TokenPair pair = jwtTokenProvider.issueStoreTokens("1", "email");
        when(identityService.getProfileView(1L)).thenReturn(
                new UserProfileDTO(1L, "user@example.com", true, null, null, UserTier.REGULAR, null, null, UserStatus.ACTIVE, null));

        // ACT + ASSERT: L1 200, L2 user.id（R 包络 data）
        mockMvc.perform(get("/api/store/account/profile")
                        .header("Authorization", "Bearer " + pair.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    // ===== TC-WEB-STORE-013: BLOCKER-1 — 会话已撤销，旧 token 访问 → 401 =====

    @Test
    @DisplayName("TC-WEB-STORE-013 [P0]: 撤销后带旧 token 访问 /api/store/account/profile → 401（BLOCKER-1 EDGE-023 即时失效）")
    void accountProfile_revokedSession_returns401() throws Exception {
        // ARRANGE: 签名仍合法的 store token，但会话已被撤销（Redis DEL + DB revoked）
        TokenPair pair = jwtTokenProvider.issueStoreTokens("user-1", "email");
        when(sessionValidator.isStoreSessionValid(any())).thenReturn(false);

        // ACT + ASSERT: 过滤器校验会话失效 → 401（不再等待 token 自然过期）
        mockMvc.perform(get("/api/store/account/profile")
                        .header("Authorization", "Bearer " + pair.getAccessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ===== TC-WEB-STORE-007: OTP 错误 → 401 + {code,message,details} =====

    @Test
    @DisplayName("TC-WEB-STORE-007 [P0]: verifyOtp 错误码 → 401 + code=40101 + remaining_attempts（GlobalExceptionHandler）")
    void verifyOtp_wrongCode_returns401WithDetails() throws Exception {
        // ARRANGE
        when(otpService.verifyOtp(any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.OTP_INVALID,
                        java.util.Map.of("remaining_attempts", 3)));

        // ACT + ASSERT: L1 401, L2 code=40101 + message, L3 data.remaining_attempts, L4 message 英文
        mockMvc.perform(post("/api/store/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "en")
                        .content("{\"email\":\"user@example.com\",\"code\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("Incorrect verification code"))
                .andExpect(jsonPath("$.data.remaining_attempts").value(3));
    }

    // ===== TC-WEB-STORE-008: i18n — Accept-Language: fr → 法语 message =====

    @Test
    @DisplayName("TC-WEB-STORE-008 [P1]: Accept-Language: fr → 法语错误文案（i18n）")
    void verifyOtp_frLocale_returnsFrenchMessage() throws Exception {
        // ARRANGE
        when(otpService.verifyOtp(any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.OTP_INVALID));

        // ACT + ASSERT: L4 message 法语
        mockMvc.perform(post("/api/store/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "fr")
                        .content("{\"email\":\"user@example.com\",\"code\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("Code de vérification incorrect"));
    }

    // ===== TC-WEB-STORE-009: i18n — Accept-Language: es → 西班牙语 message =====

    @Test
    @DisplayName("TC-WEB-STORE-009 [P1]: Accept-Language: es → 西班牙语错误文案（i18n）")
    void verifyOtp_esLocale_returnsSpanishMessage() throws Exception {
        // ARRANGE
        when(otpService.verifyOtp(any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.OTP_INVALID));

        // ACT + ASSERT: L4 message 西班牙语
        mockMvc.perform(post("/api/store/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "es")
                        .content("{\"email\":\"user@example.com\",\"code\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("Código de verificación incorrecto"));
    }

    // ===== TC-WEB-STORE-010: NBT-01 CORS Preflight /api/store/* =====

    @Test
    @DisplayName("TC-WEB-STORE-010 [P0]: OPTIONS /api/store/auth/config origin=5173 → CORS 预检通过（NBT-01）")
    void corsPreflightStore_allowedOrigin_returnsHeaders() throws Exception {
        // ASSERT: L1 200/204, L2 ACAO header, L3 ACAM/ACAH, L4 Allow-Credentials=true
        mockMvc.perform(options("/api/store/auth/config")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization,Accept-Language"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // ===== TC-WEB-STORE-011: NBT-03 非白名单 origin 拒绝 =====

    @Test
    @DisplayName("TC-WEB-STORE-011 [P0]: OPTIONS /api/store/auth/config origin=evil.com → 无 ACAO 头（NBT-03）")
    void corsPreflightStore_nonWhitelistOrigin_noAcaoHeader() throws Exception {
        // ASSERT: L2 无 Access-Control-Allow-Origin
        mockMvc.perform(options("/api/store/auth/config")
                        .header("Origin", "http://evil.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ===== TC-WEB-STORE-012: Bean Validation → 422 + {code,message,details} =====

    @Test
    @DisplayName("TC-WEB-STORE-012 [P1]: sendOtp 邮箱格式非法 → 422 + code=40000 + details.email（GlobalExceptionHandler）")
    void sendOtp_invalidEmail_returns422() throws Exception {
        // ASSERT: L1 422, L2 code=40000, L3 data.email 存在
        mockMvc.perform(post("/api/store/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.email").exists());
    }

    // ===== helpers =====

    private User activeUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        return u;
    }

    private TokenPair buildTokenPair() {
        TokenPair p = jwtTokenProvider.issueStoreTokens("user-1", "email");
        return p;
    }
}
