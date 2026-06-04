package com.dreamy.identity.domain.session.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * admin_session 表列名常量。
 * L2-REF: identity-physical-schema.md § 10 admin_session 表结构
 */
public interface AdminSessionDBConst extends CommonDBConst {

    String TABLE = "admin_session";

    String ADMIN_ID = "admin_id";
    String TOKEN_ID = "token_id";
    String IP = "ip";
    String DEVICE = "device";
    String STATUS = "status";
    String LAST_ACTIVE_AT = "last_active_at";
}
