package com.dreamy.infra.oidc;

/**
 * OIDC 验证端口（Google/Apple）。
 * 约束: FLOW-03 STEP-01（超时 5s 重试 1 次：超时→50401，不可达→50201）；RT-002/CB-001/DG-001。
 * 实现按 identity.oidc.mode=stub|real 二选一注入。
 */
public interface OidcVerifier {

    /**
     * 验证 id_token，返回 sub/email 等。
     * @param provider google | apple
     * @param idToken  待验证 id_token
     * @param nonce    可选 nonce
     * @throws com.dreamy.error.InfraException OIDC_TIMEOUT(50401)/OIDC_UNAVAILABLE(50201)
     */
    OidcResult verify(String provider, String idToken, String nonce);
}
