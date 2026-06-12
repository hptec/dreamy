package com.dreamy.domain.authconfig.consts;

import com.dreamy.consts.CommonDBConst;

/**
 * auth_config 表列名常量。
 * L2-REF: identity-physical-schema.md § 12 auth_config 表结构
 */
public interface AuthConfigDBConst extends CommonDBConst {

    String TABLE = "auth_config";

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
}
