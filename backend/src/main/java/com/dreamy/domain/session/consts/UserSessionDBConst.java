package com.dreamy.domain.session.consts;

import com.dreamy.consts.CommonDBConst;

public interface UserSessionDBConst extends CommonDBConst {

    String TABLE = "user_session";

    String USER_ID = "user_id";
    String TOKEN_ID = "token_id";
    String REFRESH_TOKEN_ID = "refresh_token_id";
    String ACCESS_EXPIRES_AT = "access_expires_at";
    String REFRESH_EXPIRES_AT = "refresh_expires_at";
    String BROWSER = "browser";
    // STATUS, VERSION, METHOD, DEVICE, IP, LOCATION, IS_NEW_DEVICE, LAST_ACTIVE_AT 继承自 CommonDBConst
}
