package com.dreamy.identity.domain.otp.repository;

/**
 * otp_code 表列名常量（DB 列 snake_case）。
 */
public interface OtpCodeDBConst {

    String ID = "id";
    String EMAIL = "email";
    String CODE_HASH = "code_hash";
    String LENGTH = "length";
    String EXPIRES_AT = "expires_at";
    String ATTEMPTS = "attempts";
    String MAX_ATTEMPTS = "max_attempts";
    String STATUS = "status";
    String LAST_SENT_AT = "last_sent_at";
    String VERSION = "version";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";
}
