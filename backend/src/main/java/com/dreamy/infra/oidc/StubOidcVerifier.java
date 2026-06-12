package com.dreamy.infra.oidc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OIDC 验证 stub 实现（沙箱默认，DG-002）。
 * 约束: stub 可注入 mock sub（id_token 直接当作 "sub|email|verified" 或回退确定性派生）。
 * identity.oidc.mode=stub 时生效。
 */
@Component
@ConditionalOnProperty(name = "identity.oidc.mode", havingValue = "stub", matchIfMissing = true)
public class StubOidcVerifier implements OidcVerifier {

    private static final Logger log = LoggerFactory.getLogger(StubOidcVerifier.class);

    @Override
    public OidcResult verify(String provider, String idToken, String nonce) {
        log.info("[OIDC-STUB] verify provider={} (token [REDACTED])", provider);
        // 约定 stub 注入格式：sub|email|emailVerified(true/false)[|relayEmail]
        String[] parts = idToken == null ? new String[0] : idToken.split("\\|");
        if (parts.length >= 3) {
            boolean hidden = "apple".equals(provider) && parts.length >= 4;
            String relay = hidden ? parts[3] : null;
            return new OidcResult(parts[0], parts[1], Boolean.parseBoolean(parts[2]), hidden, relay);
        }
        // 回退：用 token 派生确定性 sub，邮箱已验证
        String sub = provider + "-stub-" + Integer.toHexString((idToken == null ? "" : idToken).hashCode());
        return new OidcResult(sub, sub + "@oidc.stub", true, false, null);
    }
}
