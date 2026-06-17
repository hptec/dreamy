package com.dreamy.domain.admin.consts;

import com.dreamy.consts.CommonDBConst;

public interface AdminUserDBConst extends CommonDBConst {

    String TABLE = "admin_user";

    String NAME = "name";
    String EMAIL = "email";
    String PASSWORD_HASH = "password_hash";
    String ROLE_ID = "role_id";
    // STATUS, VERSION 继承自 CommonDBConst
    String LAST_LOGIN_AT = "last_login_at";
    public static final String DELETED_AT = "deleted_at";
}
