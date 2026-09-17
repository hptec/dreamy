package com.dreamy.common.security;

import com.dreamy.error.BizException;
import com.dreamy.security.AuthPrincipal;
import com.dreamy.security.GuestTokenInvalidException;
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
 * guest JWT 签发/解析分型单元测试（showroom-api-detail 0.2-1/0.2-④；2026-09-17 删码版）。
 * store/admin 签发权在 Rust server,store/admin 侧 token 由测试用同构 claims 本地构造;
 * guest 签发仍在 Java(issueShowroomGuestToken)。
 * storeKey 复用、claims showroom_id/member_id/inv_ver、过期分型 GuestTokenInvalidException(401101)、
 * 非 guest 异常一律 BizException UNAUTHORIZED(40100，CP-021 同口径)。
 */
class JwtGuestTokenTest {

    private JwtProperties props;
    private JwtTokenProvider provider;
    private SecretKey storeKey;
    private SecretKey adminKey;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.getStore().setSecret("store-test-secret-key-32chars-min");
        props.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        provider = new JwtTokenProvider(props, 86400L);
        storeKey = Keys.hmacShaKeyFor(props.getStore().getSecret().getBytes(StandardCharsets.UTF_8));
        adminKey = Keys.hmacShaKeyFor(props.getAdmin().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 与 Rust issue_store claims 同构;exp 相对 now 偏移秒数(负值=已过期) */
    private String buildStoreToken(String userId, String method, boolean refresh, long expOffsetSeconds) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim("typ", AuthPrincipal.TYPE_STORE)
                .claim("method", method)
                .claim("refresh", refresh)
                .issuedAt(Date.from(now.toInstant(ZoneOffset.UTC)))
                .expiration(Date.from(now.plusSeconds(expOffsetSeconds).toInstant(ZoneOffset.UTC)))
                .signWith(storeKey)
                .compact();
    }

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
    @DisplayName("guest 签发/解析往返：claims showroom_id/member_id/inv_ver + sub=member_id + typ=guest")
    void issueAndParseGuestToken() {
        JwtTokenProvider.GuestToken token = provider.issueShowroomGuestToken(77L, 12L, 3L);
        assertThat(token.token()).isNotBlank();
        assertThat(token.tokenId()).isNotBlank();

        JwtTokenProvider.StoreBearer bearer = provider.parseStoreBearer(token.token());
        assertThat(bearer.isGuest()).isTrue();
        assertThat(bearer.guest().showroomId()).isEqualTo(12L);
        assertThat(bearer.guest().memberId()).isEqualTo(77L);
        assertThat(bearer.guest().inviteVersion()).isEqualTo(3L);
        assertThat(bearer.guest().subject()).isEqualTo("77");
        assertThat(bearer.guest().tokenId()).isEqualTo(token.tokenId());
    }

    @Test
    @DisplayName("store token 经 parseStoreBearer 走 store 分型，principal 语义与 parseStoreToken 一致")
    void parseStoreBearerStoreTyping() {
        String storeToken = buildStoreToken("user-9", "email", false, 7200);
        JwtTokenProvider.StoreBearer bearer = provider.parseStoreBearer(storeToken);
        assertThat(bearer.isGuest()).isFalse();
        assertThat(bearer.principal().subject()).isEqualTo("user-9");
        assertThat(bearer.principal().type()).isEqualTo(AuthPrincipal.TYPE_STORE);
        assertThat(bearer.principal().tokenId()).isNotBlank();
    }

    @Test
    @DisplayName("guest 过期 → GuestTokenInvalidException（401101 分型，区别于 store 过期 40100）")
    void expiredGuestTokenThrowsGuestInvalid() {
        JwtTokenProvider expiredIssuer = new JwtTokenProvider(props, -60L); // 已过期
        JwtTokenProvider.GuestToken token = expiredIssuer.issueShowroomGuestToken(77L, 12L, 3L);
        assertThatThrownBy(() -> provider.parseStoreBearer(token.token()))
                .isInstanceOf(GuestTokenInvalidException.class);
    }

    @Test
    @DisplayName("admin token（跨端密钥）/垃圾串 → BizException UNAUTHORIZED 40100（CP-021）")
    void crossEndAndGarbageRejectedAs40100() {
        assertThatThrownBy(() -> provider.parseStoreBearer(buildAdminToken("admin-1", "role-1")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> provider.parseStoreBearer("not-a-jwt"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("store token 过期 → BizException 40100（既有口径不受 guest 分型影响）")
    void expiredStoreTokenStays40100() {
        String expired = buildStoreToken("user-1", "email", false, -60);
        assertThatThrownBy(() -> provider.parseStoreBearer(expired))
                .isInstanceOf(BizException.class)
                .isNotInstanceOf(GuestTokenInvalidException.class);
    }
}
