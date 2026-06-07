package com.dreamy.identity.domain.otp.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

public interface OtpCodeDBConst extends CommonDBConst {

    String TABLE = "otp_code";

    String EMAIL = "email";
    String CODE_HASH = "code_hash";
    String LENGTH = "length";
    String EXPIRES_AT = "expires_at";
    String ATTEMPTS = "attempts";
    String MAX_ATTEMPTS = "max_attempts";
    // STATUS, VERSION 继承自 CommonDBConst
    String LAST_SENT_AT = "last_sent_at";
}
