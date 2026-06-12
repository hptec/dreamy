package com.dreamy.common.security;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.security.AuthPrincipal;
import com.dreamy.security.JwtProperties;
import com.dreamy.security.JwtTokenProvider;
import com.dreamy.security.TokenPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UT-06 JWT 生成/解析/过期/跨端隔离单元测试。
 * 约束: shared-contracts jwt_isolation；EDGE-024 跨端误用 40100；DR-01 独立密钥。
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.getStore().setSecret("store-test-secret-key-32chars-min");
        props.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        provider = new JwtTokenProvider(props);
    }

    @Test
    @DisplayName("TC-UNIT-020: store access token 签发/解析正确")
    void issueAndParseStoreToken() {
        TokenPair pair = provider.issueStoreTokens("user-1", "email");
        AuthPrincipal p = provider.parseStoreToken(pair.getAccessToken());
        assertThat(p.subject()).isEqualTo("user-1");
        assertThat(p.type()).isEqualTo(AuthPrincipal.TYPE_STORE);
        assertThat(p.method()).isEqualTo("email");
        assertThat(p.refresh()).isFalse();
    }

    @Test
    @DisplayName("TC-UNIT-021: store refresh token 解析 refresh=true")
    void parseStoreRefreshToken() {
        TokenPair pair = provider.issueStoreTokens("user-1", "google");
        AuthPrincipal p = provider.parseStoreToken(pair.getRefreshToken());
        assertThat(p.refresh()).isTrue();
        assertThat(p.tokenId()).isEqualTo(pair.getRefreshTokenId());
    }

    @Test
    @DisplayName("TC-UNIT-022: admin token 签发/解析（不含 permission_keys，权限实时查 DB）")
    void issueAndParseAdminToken() {
        var token = provider.issueAdminToken("admin-1", "role-1");
        AuthPrincipal p = provider.parseAdminToken(token.token());
        assertThat(p.subject()).isEqualTo("admin-1");
        assertThat(p.type()).isEqualTo(AuthPrincipal.TYPE_ADMIN);
        assertThat(p.permissionKeys()).isNull();
    }

    @Test
    @DisplayName("TC-UNIT-023: store token 用 admin 密钥解析 → 40100 UNAUTHORIZED（EDGE-024）")
    void crossUse_storeTokenOnAdminParser_throws40100() {
        TokenPair pair = provider.issueStoreTokens("user-1", "email");
        assertThatThrownBy(() -> provider.parseAdminToken(pair.getAccessToken()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("TC-UNIT-024: admin token 用 store 密钥解析 → 40100 UNAUTHORIZED（EDGE-024）")
    void crossUse_adminTokenOnStoreParser_throws40100() {
        var token = provider.issueAdminToken("admin-1", "role-1");
        assertThatThrownBy(() -> provider.parseStoreToken(token.token()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("TC-UNIT-025: 篡改 token → 40100 UNAUTHORIZED")
    void tamperedToken_throws40100() {
        TokenPair pair = provider.issueStoreTokens("user-1", "email");
        String tampered = pair.getAccessToken() + "x";
        assertThatThrownBy(() -> provider.parseStoreToken(tampered))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
