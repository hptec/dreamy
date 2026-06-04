package com.dreamy.identity.domain.audit.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * login_history 表列名常量。
 * L2-REF: identity-physical-schema.md § 5 login_history 表结构
 */
public interface LoginHistoryDBConst extends CommonDBConst {

    String TABLE = "login_history";

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
