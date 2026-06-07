package com.dreamy.identity.infra.oidc;

/**
 * OIDC 验证结果（id_token 校验后取 sub/email/email_verified；Apple hidden→relay_email）。
 * 约束: FLOW-03 STEP-01；FUNC-004/005/028/029。
 * name/picture/locale 仅在 provider=google 且请求 profile scope 时有值。
 */
public record OidcResult(
        String sub,
        String email,
        boolean emailVerified,
        boolean hiddenEmail,
        String relayEmail,
        String name,
        String picture,
        String locale
) {
    /** 向后兼容：无 profile 字段的调用方（email/stub）。 */
    public OidcResult(String sub, String email, boolean emailVerified,
                      boolean hiddenEmail, String relayEmail) {
        this(sub, email, emailVerified, hiddenEmail, relayEmail, null, null, null);
    }
}
