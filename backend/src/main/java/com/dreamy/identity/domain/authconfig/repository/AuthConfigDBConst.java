package com.dreamy.identity.domain.authconfig.repository;

/**
 * auth_config 表列名常量（DB 列 snake_case，单例 id=1）。
 */
public interface AuthConfigDBConst {

    String ID = "id";
    String EMAIL_ENABLED = "email_enabled";
    String GOOGLE_ENABLED = "google_enabled";
    String APPLE_ENABLED = "apple_enabled";
    String OTP_LENGTH = "otp_length";
    String OTP_TTL_MINUTES = "otp_ttl_minutes";
    String OTP_RESEND_SECONDS = "otp_resend_seconds";
    String OTP_MAX_ATTEMPTS = "otp_max_attempts";
    String MIN_METHODS = "min_methods";
    String GOOGLE_CLIENT_ID = "google_client_id";
    String APPLE_SERVICE_ID = "apple_service_id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";
}
