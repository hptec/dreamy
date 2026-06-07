package com.dreamy.identity.domain.audit.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

public interface LoginHistoryDBConst extends CommonDBConst {

    String TABLE = "login_history";

    String USER_ID = "user_id";
    String EMAIL = "email";
    // METHOD, IP, DEVICE, LOCATION, IS_NEW_DEVICE 继承自 CommonDBConst
    String RESULT = "result";
    String NOTIFIED = "notified";
}
