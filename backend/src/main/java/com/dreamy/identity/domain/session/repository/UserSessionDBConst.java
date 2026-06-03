package com.dreamy.identity.domain.session.repository;

/**
 * user_session 表列名常量。供 MyBatis-Plus Wrapper / SQL 引用，避免硬编码列名。
 */
public interface UserSessionDBConst {

    String TABLE = "user_session";

    String ID = "id";
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
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";
}
