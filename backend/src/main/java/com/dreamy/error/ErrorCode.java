package com.dreamy.error;

import lombok.Getter;

/**
 * 统一错误码枚举（25 码）。权威来源 architecture/error-strategy.md 码表。
 * 约束: shared-contracts error_envelope（code 数字码，高3位对应 HTTP 状态）；message key 用于 i18n 解析。
 * store 端按 Accept-Language(en/es/fr) 本地化；admin 端固定中文。
 */
@Getter
public enum ErrorCode {

    // ===== 400 参数错误 =====
    VALIDATION_ERROR(40000, 422, "error.40000"),
    INVALID_EMAIL(40001, 422, "error.40001"),
    CONFIG_OUT_OF_RANGE(40002, 422, "error.40002"),

    // ===== 401 未认证 =====
    UNAUTHORIZED(40100, 401, "error.40100"),
    OTP_INVALID(40101, 401, "error.40101"),
    REFRESH_INVALID(40102, 401, "error.40102"),
    CREDENTIALS_INVALID(40103, 401, "error.40103"),

    // ===== 403 无权限 / 业务禁止 =====
    FORBIDDEN(40300, 403, "error.40300"),
    ACCOUNT_DISABLED(40301, 403, "error.40301"),
    ADMIN_DISABLED(40302, 403, "error.40302"),
    PROVIDER_DISABLED(40303, 403, "error.40303"),
    PRIMARY_EMAIL_REQUIRED(40304, 403, "error.40304"),
    MIN_METHODS_REQUIRED(40305, 403, "error.40305"),
    SUPER_ADMIN_PROTECTED(40306, 403, "error.40306"),
    CANNOT_DELETE_SELF(40307, 403, "error.40307"),
    ROLE_LOCKED(40308, 403, "error.40308"),

    // ===== 404 不存在 =====
    NOT_FOUND(40400, 404, "error.40400"),

    // ===== 409 冲突 =====
    EMAIL_EXISTS(40901, 409, "error.40901"),
    EMAIL_CONFLICT_UNVERIFIED(40902, 409, "error.40902"),
    IDENTITY_TAKEN(40903, 409, "error.40903"),
    ROLE_IN_USE(40904, 409, "error.40904"),

    // ===== 410 失效 =====
    OTP_EXPIRED(41001, 410, "error.41001"),
    OTP_LOCKED(41002, 410, "error.41002"),

    // ===== 429 限流 =====
    RESEND_TOO_SOON(42901, 429, "error.42901"),
    RATE_LIMITED(42902, 429, "error.42902"),

    // ===== 500 / 502 / 504 服务端与外部集成 =====
    INTERNAL_ERROR(50000, 500, "error.50000"),
    DATABASE_ERROR(50001, 500, "error.50001"),
    EMAIL_SEND_FAILED(50002, 500, "error.50002"),
    OIDC_UNAVAILABLE(50201, 502, "error.50201"),
    OIDC_TIMEOUT(50401, 504, "error.50401");

    /** 数字业务码 */
    private final int code;
    /** HTTP 状态码 */
    private final int httpStatus;
    /** i18n message key */
    private final String messageKey;

    ErrorCode(int code, int httpStatus, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
