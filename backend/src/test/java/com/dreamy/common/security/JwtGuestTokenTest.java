package com.dreamy.common.security;

import com.dreamy.error.BizException;
import com.dreamy.security.AuthPrincipal;
import com.dreamy.security.GuestTokenInvalidException;
import com.dreamy.security.JwtProperties;
import com.dreamy.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * guest JWT 签发/解析分型单元测试（showroom-api-detail 0.2-1/0.2-④）。
 * storeKey 复用、claims showroom_id/member_id/inv_ver、过期分型 GuestTokenInvalidException(401101)、
 * 非 guest 异常一律 BizException UNAUTHORIZED(40100，CP-021 同口径)。
 */
class JwtGuestTokenTest {

    private JwtProperties props;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.getStore().setSecret("store-test-secret-key-32chars-min");
        props.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        provider = new JwtTokenProvider(props, 86400L);
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
        var pair = provider.issueStoreTokens("user-9", "email");
        JwtTokenProvider.StoreBearer bearer = provider.parseStoreBearer(pair.getAccessToken());
        assertThat(bearer.isGuest()).isFalse();
        assertThat(bearer.principal().subject()).isEqualTo("user-9");
        assertThat(bearer.principal().type()).isEqualTo(AuthPrincipal.TYPE_STORE);
        assertThat(bearer.principal().tokenId()).isEqualTo(pair.getTokenId());
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
        var adminToken = provider.issueAdminToken("admin-1", "role-1");
        assertThatThrownBy(() -> provider.parseStoreBearer(adminToken.token()))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> provider.parseStoreBearer("not-a-jwt"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("store token 过期 → BizException 40100（既有口径不受 guest 分型影响）")
    void expiredStoreTokenStays40100() {
        JwtProperties shortProps = new JwtProperties();
        shortProps.getStore().setSecret("store-test-secret-key-32chars-min");
        shortProps.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        shortProps.getStore().setAccessTtlSeconds(-60);
        JwtTokenProvider expiredIssuer = new JwtTokenProvider(shortProps, 86400L);
        var pair = expiredIssuer.issueStoreTokens("user-1", "email");
        assertThatThrownBy(() -> provider.parseStoreBearer(pair.getAccessToken()))
                .isInstanceOf(BizException.class)
                .isNotInstanceOf(GuestTokenInvalidException.class);
    }
}
