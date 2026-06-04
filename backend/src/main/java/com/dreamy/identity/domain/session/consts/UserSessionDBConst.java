package com.dreamy.identity.domain.session.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * user_session 表列名常量。
 * L2-REF: identity-physical-schema.md § 4 user_session 表结构
 */
public interface UserSessionDBConst extends CommonDBConst {

    String TABLE = "user_session";

    String USER_ID = "user_id";
    String TOKEN_ID = "token_id";
    String REFRESH_TOKEN_ID = "refresh_token_id";
    String ACCESS_EXPIRES_AT = "access_expires_at";
    String REFRESH_EXPIRES_AT = "refresh_expires_at";
    String DEVICE = "device";
    String BROWSER = "browser";
    String IP = "ip";
    String LOCATION = "location";
    String IS_NEW_DEVICE = "is_new_device";
    String METHOD = "method";
    String STATUS = "status";
    String LAST_ACTIVE_AT = "last_active_at";
    String VERSION = "version";
}
