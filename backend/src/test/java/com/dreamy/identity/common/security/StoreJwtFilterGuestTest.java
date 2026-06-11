package com.dreamy.identity.common.security;

import com.dreamy.identity.infra.SessionValidator;
import com.dreamy.identity.security.AuthContext;
import com.dreamy.identity.security.AuthPrincipal;
import com.dreamy.identity.security.GuestContext;
import com.dreamy.identity.security.JwtProperties;
import com.dreamy.identity.security.JwtTokenProvider;
import com.dreamy.identity.security.ShowroomGuestValidator;
import com.dreamy.identity.security.StoreJwtFilter;
import com.dreamy.identity.security.StoreSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * StoreJwtFilter 四段裁决单元测试（showroom-api-detail 0.2-2 权威设计）：
 * ① method-aware 公开白名单（principal 可选注入）② store 既有链路 ③ guest 旁路
 * （作用域/操作白名单/showroom_id 等值/ShowroomGuestValidator）④ 过期分型 401101/40100。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreJwtFilterGuestTest {

    @Mock
    private SessionValidator sessionValidator;
    @Mock
    private ObjectProvider<ShowroomGuestValidator> validatorProvider;
    @Mock
    private ShowroomGuestValidator guestValidator;

    private JwtTokenProvider provider;
    private StoreJwtFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.getStore().setSecret("store-test-secret-key-32chars-min");
        props.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        provider = new JwtTokenProvider(props, 86400L);

        StoreSecurityProperties security = new StoreSecurityProperties();
        security.setStorePublicPaths(List.of(
                "/api/store/auth/otp/send",
                "/api/store/auth/otp/verify",
                "/api/store/auth/oidc/**",
                "/api/store/auth/refresh",
                "/api/store/auth/config",
                "/api/store/products/**",
                "GET:/api/store/reviews",
                "POST:/api/store/payments/stripe/webhook",
                "POST:/api/store/showrooms/guest-session"));
        security.setShowroomGuestPaths(List.of(
                "GET:/api/store/showrooms/*",
                "PUT:/api/store/showrooms/*/items/*/vote",
                "POST:/api/store/showrooms/*/items/*/comments"));

        when(validatorProvider.getIfAvailable()).thenReturn(guestValidator);
        filter = new StoreJwtFilter(provider, sessionValidator, security, validatorProvider);
    }

    private MockHttpServletResponse run(String method, String path, String token, FilterChain chain)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletResponse run(String method, String path, String token)
            throws ServletException, IOException {
        return run(method, path, token, new MockFilterChain());
    }

    // ===== ① 公开白名单 =====

    @Test
    @DisplayName("identity 既有公开路径配置化后无 token 放行（回归保持）")
    void legacyPublicPathAnonymousPass() throws Exception {
        MockHttpServletResponse response = run("POST", "/api/store/auth/otp/send", null);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("method-aware：GET /api/store/reviews 公开，POST 同路径无 token → 401 40100")
    void methodAwarePublicVsProtected() throws Exception {
        assertThat(run("GET", "/api/store/reviews", null).getStatus()).isEqualTo(200);
        MockHttpServletResponse post = run("POST", "/api/store/reviews", null);
        assertThat(post.getStatus()).isEqualTo(401);
        assertThat(post.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("stripe webhook POST 豁免 JWT（验签由 Stripe 签名过滤器接管）")
    void stripeWebhookExemptFromJwt() throws Exception {
        assertThat(run("POST", "/api/store/payments/stripe/webhook", null).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("白名单 principal 可选注入：有效 store token → 注入；垃圾 token → 匿名放行不报错")
    void publicPathOptionalPrincipalInjection() throws Exception {
        var pair = provider.issueStoreTokens("user-1", "email");
        when(sessionValidator.isStoreSessionValid(pair.getTokenId())).thenReturn(true);
        AtomicReference<AuthPrincipal> seen = new AtomicReference<>();
        FilterChain capturing = (req, res) -> seen.set(AuthContext.get());

        MockHttpServletResponse ok = run("POST", "/api/store/showrooms/guest-session",
                pair.getAccessToken(), capturing);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().subject()).isEqualTo("user-1");

        seen.set(null);
        MockHttpServletResponse anon = run("POST", "/api/store/showrooms/guest-session",
                "garbage-token", capturing);
        assertThat(anon.getStatus()).isEqualTo(200);
        assertThat(seen.get()).isNull();
    }

    // ===== ② store 既有链路 =====

    @Test
    @DisplayName("store token 会话有效 → 放行注入 principal；会话失效 → 401 40100（EDGE-023）")
    void storeTokenSessionValidation() throws Exception {
        var pair = provider.issueStoreTokens("user-2", "email");
        when(sessionValidator.isStoreSessionValid(pair.getTokenId())).thenReturn(true);
        AtomicReference<AuthPrincipal> seen = new AtomicReference<>();
        MockHttpServletResponse ok = run("GET", "/api/store/cart", pair.getAccessToken(),
                (req, res) -> seen.set(AuthContext.get()));
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(seen.get().type()).isEqualTo(AuthPrincipal.TYPE_STORE);

        when(sessionValidator.isStoreSessionValid(pair.getTokenId())).thenReturn(false);
        MockHttpServletResponse revoked = run("GET", "/api/store/cart", pair.getAccessToken());
        assertThat(revoked.getStatus()).isEqualTo(401);
        assertThat(revoked.getContentAsString()).contains("\"code\":40100");
    }

    // ===== ③ guest 旁路 =====

    private String guestToken(long memberId, long showroomId, long invVer) {
        return provider.issueShowroomGuestToken(memberId, showroomId, invVer).token();
    }

    @Test
    @DisplayName("③a guest token 用于非 showrooms 端点 → 401 40100（跨用途误用 CP-021）")
    void guestOutsideScope() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/store/cart", guestToken(7, 12, 1));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("③b guest 操作白名单外（DELETE showroom / GET 列表 / items 增删）→ 403 403102")
    void guestOperationNotWhitelisted() throws Exception {
        for (String[] call : new String[][]{
                {"DELETE", "/api/store/showrooms/12"},
                {"GET", "/api/store/showrooms"},
                {"POST", "/api/store/showrooms/12/items"},
                {"POST", "/api/store/showrooms/12/members/7/assign"}}) {
            MockHttpServletResponse response = run(call[0], call[1], guestToken(7, 12, 1));
            assertThat(response.getStatus()).as(call[1]).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("\"code\":403102");
        }
    }

    @Test
    @DisplayName("③c 路径 {id} != claims.showroom_id → 403 403102（guest 越权非绑定房）")
    void guestShowroomIdMismatch() throws Exception {
        MockHttpServletResponse response = run("GET", "/api/store/showrooms/99", guestToken(7, 12, 1));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":403102");
    }

    @Test
    @DisplayName("③d 行不存在/invite_version 不等 → 401 401101（邀请重置即时失效）")
    void guestValidatorRejects() throws Exception {
        when(guestValidator.isGuestSessionValid(12L, 1L)).thenReturn(false);
        MockHttpServletResponse response = run("GET", "/api/store/showrooms/12", guestToken(7, 12, 1));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":401101");
    }

    @Test
    @DisplayName("③d showroom 域未装配（validator 缺席）→ fail-closed 401 401101")
    void guestValidatorAbsentFailClosed() throws Exception {
        when(validatorProvider.getIfAvailable()).thenReturn(null);
        MockHttpServletResponse response = run("GET", "/api/store/showrooms/12", guestToken(7, 12, 1));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":401101");
    }

    @Test
    @DisplayName("③e guest 校验通过 → 注入 typ=guest principal + GuestContext，三条操作白名单全放行")
    void guestHappyPathInjectsPrincipalAndContext() throws Exception {
        when(guestValidator.isGuestSessionValid(12L, 1L)).thenReturn(true);
        AtomicReference<AuthPrincipal> principal = new AtomicReference<>();
        AtomicReference<GuestContext> guestCtx = new AtomicReference<>();
        FilterChain capturing = (req, res) -> {
            principal.set(AuthContext.get());
            guestCtx.set(GuestContext.get());
        };

        for (String[] call : new String[][]{
                {"GET", "/api/store/showrooms/12"},
                {"PUT", "/api/store/showrooms/12/items/5/vote"},
                {"POST", "/api/store/showrooms/12/items/5/comments"}}) {
            principal.set(null);
            guestCtx.set(null);
            MockHttpServletResponse response = run(call[0], call[1], guestToken(7, 12, 1), capturing);
            assertThat(response.getStatus()).as(call[1]).isEqualTo(200);
            assertThat(principal.get().type()).isEqualTo(AuthPrincipal.TYPE_GUEST);
            assertThat(principal.get().subject()).isEqualTo("7");
            assertThat(guestCtx.get().showroomId()).isEqualTo(12L);
            assertThat(guestCtx.get().memberId()).isEqualTo(7L);
        }
        // 请求结束后 ThreadLocal 清理
        assertThat(AuthContext.get()).isNull();
        assertThat(GuestContext.get()).isNull();
    }

    // ===== ④ 解析异常分型 =====

    @Test
    @DisplayName("④ guest 过期 → 401 401101；签名非法/无 token → 401 40100")
    void parseExceptionTyping() throws Exception {
        JwtProperties props = new JwtProperties();
        props.getStore().setSecret("store-test-secret-key-32chars-min");
        props.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        String expiredGuest = new JwtTokenProvider(props, -60L)
                .issueShowroomGuestToken(7, 12, 1).token();
        MockHttpServletResponse expired = run("GET", "/api/store/showrooms/12", expiredGuest);
        assertThat(expired.getStatus()).isEqualTo(401);
        assertThat(expired.getContentAsString()).contains("\"code\":401101");

        MockHttpServletResponse garbage = run("GET", "/api/store/cart", "garbage");
        assertThat(garbage.getStatus()).isEqualTo(401);
        assertThat(garbage.getContentAsString()).contains("\"code\":40100");

        MockHttpServletResponse missing = run("GET", "/api/store/cart", null);
        assertThat(missing.getStatus()).isEqualTo(401);
        assertThat(missing.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("跨端误用：admin token 打 store 端点 → 401 40100（EDGE-024/CP-021 回归）")
    void crossEndAdminTokenRejected() throws Exception {
        var adminToken = provider.issueAdminToken("admin-1", "role-1");
        MockHttpServletResponse response = run("GET", "/api/store/cart", adminToken.token());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }
}
