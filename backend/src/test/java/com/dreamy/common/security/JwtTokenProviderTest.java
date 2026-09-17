package com.dreamy.common.security;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.security.AuthPrincipal;
import com.dreamy.security.JwtProperties;
import com.dreamy.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 解析/过期/跨端隔离单元测试(2026-09-17 Java 删码版)。
 * store/admin 签发权在 Rust server;本测试用同构 claims 本地构造 token,验证 Java 侧
 * 验签解析契约(HS256 共享密钥互认、typ 分型拒收、篡改拒绝)。
 * 约束: shared-contracts jwt_isolation；EDGE-024 跨端误用 40100；DR-01 独立密钥。
 */
class JwtTokenProviderTest {

    private JwtProperties props;
    private JwtTokenProvider provider;
    private SecretKey storeKey;
    private SecretKey adminKey;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.getStore().setSecret("store-test-secret-key-32chars-min");
        props.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        provider = new JwtTokenProvider(props);
        storeKey = Keys.hmacShaKeyFor(props.getStore().getSecret().getBytes(StandardCharsets.UTF_8));
        adminKey = Keys.hmacShaKeyFor(props.getAdmin().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 与 Rust issue_store claims 同构(typ/method/refresh claim 名一字不差) */
    private String buildStoreToken(String userId, String method, boolean refresh) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim("typ", AuthPrincipal.TYPE_STORE)
                .claim("method", method)
                .claim("refresh", refresh)
                .issuedAt(Date.from(now.toInstant(ZoneOffset.UTC)))
                .expiration(Date.from(now.plusSeconds(7200).toInstant(ZoneOffset.UTC)))
                .signWith(storeKey)
                .compact();
    }

    /** 与 Rust issue_admin claims 同构(role_id 宽容解析兼容 String/数字) */
    private String buildAdminToken(String adminId, String roleId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return Jwts.builder()
                .subject(adminId)
                .id(UUID.randomUUID().toString())
                .claim("typ", AuthPrincipal.TYPE_ADMIN)
                .claim("role_id", roleId)
                .claim("refresh", false)
                .issuedAt(Date.from(now.toInstant(ZoneOffset.UTC)))
                .expiration(Date.from(now.plusSeconds(28800).toInstant(ZoneOffset.UTC)))
                .signWith(adminKey)
                .compact();
    }

    @Test
    @DisplayName("TC-UNIT-020: store token 解析正确(claims 与 Rust 签发同构)")
    void parseStoreToken() {
        AuthPrincipal p = provider.parseStoreToken(buildStoreToken("user-1", "email", false));
        assertThat(p.subject()).isEqualTo("user-1");
        assertThat(p.type()).isEqualTo(AuthPrincipal.TYPE_STORE);
        assertThat(p.method()).isEqualTo("email");
        assertThat(p.refresh()).isFalse();
    }

    @Test
    @DisplayName("TC-UNIT-021: store refresh token 解析 refresh=true")
    void parseStoreRefreshToken() {
        AuthPrincipal p = provider.parseStoreToken(buildStoreToken("user-1", "google", true));
        assertThat(p.refresh()).isTrue();
        assertThat(p.tokenId()).isNotBlank();
    }

    @Test
    @DisplayName("TC-UNIT-022: admin token 解析（不含 permission_keys，权限实时查 IdentityGate）")
    void parseAdminToken() {
        AuthPrincipal p = provider.parseAdminToken(buildAdminToken("admin-1", "role-1"));
        assertThat(p.subject()).isEqualTo("admin-1");
        assertThat(p.type()).isEqualTo(AuthPrincipal.TYPE_ADMIN);
        assertThat(p.permissionKeys()).isNull();
    }

    @Test
    @DisplayName("TC-UNIT-023: store token 用 admin 密钥解析 → 40100 UNAUTHORIZED（EDGE-024）")
    void crossUse_storeTokenOnAdminParser_throws40100() {
        assertThatThrownBy(() -> provider.parseAdminToken(buildStoreToken("user-1", "email", false)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("TC-UNIT-024: admin token 用 store 密钥解析 → 40100 UNAUTHORIZED（EDGE-024）")
    void crossUse_adminTokenOnStoreParser_throws40100() {
        assertThatThrownBy(() -> provider.parseStoreToken(buildAdminToken("admin-1", "role-1")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("TC-UNIT-025: 篡改 token → 40100 UNAUTHORIZED")
    void tamperedToken_throws40100() {
        String tampered = buildStoreToken("user-1", "email", false) + "x";
        assertThatThrownBy(() -> provider.parseStoreToken(tampered))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("JWT 密钥为空、过短或两端复用时启动失败")
    void rejectsUnsafeSigningKeys() {
        JwtProperties blank = new JwtProperties();
        blank.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        assertThatThrownBy(() -> new JwtTokenProvider(blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORE_JWT_SECRET");

        JwtProperties shortKey = new JwtProperties();
        shortKey.getStore().setSecret("too-short");
        shortKey.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        assertThatThrownBy(() -> new JwtTokenProvider(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");

        JwtProperties shared = new JwtProperties();
        String sameSecret = "shared-test-secret-key-at-least-32-bytes";
        shared.getStore().setSecret(sameSecret);
        shared.getAdmin().setSecret(sameSecret);
        assertThatThrownBy(() -> new JwtTokenProvider(shared))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be different");
    }
}
