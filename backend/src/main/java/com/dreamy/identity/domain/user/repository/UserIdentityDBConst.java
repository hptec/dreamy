package com.dreamy.identity.domain.user.repository;

/**
 * user_identity 表列名常量（huihao-mysql 规范）。
 * 含 LongAuditableEntity 基类列 id / created_at / updated_at。
 */
public interface UserIdentityDBConst {

    String TABLE = "user_identity";

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

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
