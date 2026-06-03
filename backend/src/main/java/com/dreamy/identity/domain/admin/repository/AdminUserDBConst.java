package com.dreamy.identity.domain.admin.repository;

/**
 * admin_user 表列名常量。供 MyBatis-Plus Wrapper / SQL 引用，避免硬编码列名。
 */
public interface AdminUserDBConst {

    String TABLE = "admin_user";

    String ID = "id";
    String NAME = "name";
    String EMAIL = "email";
    String PASSWORD_HASH = "password_hash";
    String ROLE_ID = "role_id";
    String STATUS = "status";
    String LAST_LOGIN_AT = "last_login_at";
    String VERSION = "version";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";
}
