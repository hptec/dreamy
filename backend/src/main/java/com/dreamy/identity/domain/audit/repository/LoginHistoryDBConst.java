package com.dreamy.identity.domain.audit.repository;

/**
 * login_history 表列名常量（huihao-mysql 规范）。
 * 含 LongAuditableEntity 基类列 id / created_at / updated_at。
 */
public interface LoginHistoryDBConst {

    String TABLE = "login_history";

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    String USER_ID = "user_id";
    String EMAIL = "email";
    String METHOD = "method";
    String IP = "ip";
    String DEVICE = "device";
    String LOCATION = "location";
    String RESULT = "result";
    String IS_NEW_DEVICE = "is_new_device";
    String NOTIFIED = "notified";
}
