package com.dreamy.identity.domain.otp.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * otp_code 表列名常量。
 * L2-REF: identity-physical-schema.md § 3 otp_code 表结构
 */
public interface OtpCodeDBConst extends CommonDBConst {

    String TABLE = "otp_code";

    String EMAIL = "email";
    String CODE_HASH = "code_hash";
    String LENGTH = "length";
    String EXPIRES_AT = "expires_at";
    String ATTEMPTS = "attempts";
    String MAX_ATTEMPTS = "max_attempts";
    String STATUS = "status";
    String LAST_SENT_AT = "last_sent_at";
    String VERSION = "version";
}
