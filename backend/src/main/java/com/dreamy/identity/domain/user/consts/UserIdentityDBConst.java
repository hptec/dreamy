package com.dreamy.identity.domain.user.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * user_identity 表列名常量。
 * L2-REF: identity-physical-schema.md § 2 user_identity 表结构
 */
public interface UserIdentityDBConst extends CommonDBConst {

    String TABLE = "user_identity";

    String USER_ID = "user_id";
    String PROVIDER = "provider";
    String PROVIDER_UID = "provider_uid";
    String IDENTIFIER = "identifier";
    String IS_PRIMARY = "is_primary";
    String VERIFIED = "verified";
    String CONNECTED = "connected";
    String HIDDEN_EMAIL = "hidden_email";
    String RELAY_EMAIL = "relay_email";
    String RELAY_VALID = "relay_valid";
    String BOUND_AT = "bound_at";
    String LAST_LOGIN_AT = "last_login_at";
}
