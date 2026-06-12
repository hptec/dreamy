package com.dreamy.domain.user.consts;

import com.dreamy.consts.CommonDBConst;

public interface UserDBConst extends CommonDBConst {

    String TABLE = "user";

    String EMAIL = "email";
    String EMAIL_VERIFIED = "email_verified";
    String NAME = "name";
    String PHONE = "phone";
    String TIER = "tier";
    // STATUS, VERSION 继承自 CommonDBConst
    String AVATAR = "avatar";
    String JOINED_AT = "joined_at";
    String DELETED_AT = "deleted_at";
    String ANONYMIZED = "anonymized";
    String ANONYMIZED_AT = "anonymized_at";
}
