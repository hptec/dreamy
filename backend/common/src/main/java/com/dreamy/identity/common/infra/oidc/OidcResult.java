package com.dreamy.identity.common.infra.oidc;

/**
 * OIDC 验证结果（id_token 校验后取 sub/email/email_verified；Apple hidden→relay_email）。
 * 约束: FLOW-03 STEP-01；FUNC-004/005/028/029。
 */
public record OidcResult(
        String sub,
        String email,
        boolean emailVerified,
        boolean hiddenEmail,
        String relayEmail
) {
}
