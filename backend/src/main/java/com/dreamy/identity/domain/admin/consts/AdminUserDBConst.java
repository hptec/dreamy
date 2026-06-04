package com.dreamy.identity.domain.admin.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * admin_user 表列名常量。
 * L2-REF: identity-physical-schema.md § 6 admin_user 表结构
 */
public interface AdminUserDBConst extends CommonDBConst {

    String TABLE = "admin_user";

    String NAME = "name";
    String EMAIL = "email";
    String PASSWORD_HASH = "password_hash";
    String ROLE_ID = "role_id";
    String STATUS = "status";
    String LAST_LOGIN_AT = "last_login_at";
    String VERSION = "version";
}
