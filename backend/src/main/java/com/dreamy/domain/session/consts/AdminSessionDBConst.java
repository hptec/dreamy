package com.dreamy.domain.session.consts;

import com.dreamy.consts.CommonDBConst;

public interface AdminSessionDBConst extends CommonDBConst {

    String TABLE = "admin_session";

    String ADMIN_ID = "admin_id";
    String TOKEN_ID = "token_id";
    // STATUS, DEVICE, IP, LAST_ACTIVE_AT 继承自 CommonDBConst
}
