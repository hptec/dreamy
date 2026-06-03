package com.dreamy.identity.domain.session.repository;

/**
 * admin_session 表列名常量。供 MyBatis-Plus Wrapper / SQL 引用，避免硬编码列名。
 */
public interface AdminSessionDBConst {

    String TABLE = "admin_session";

    String ID = "id";
    String ADMIN_ID = "admin_id";
    String TOKEN_ID = "token_id";
    String IP = "ip";
    String DEVICE = "device";
    String STATUS = "status";
    String LAST_ACTIVE_AT = "last_active_at";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";
}
