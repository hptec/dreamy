package com.dreamy.domain.user.consts;

import com.dreamy.consts.CommonDBConst;

public interface UserDBConst extends CommonDBConst {

    String TABLE = "user";

    String EMAIL = "email";
    String EMAIL_VERIFIED = "email_verified";
    /** 用户偏好语言 en/es/fr（决策13 / FUNC-019，V20260616 增量加列） */
    String LOCALE_PREF = "locale_pref";
    String NAME = "name";
    String PHONE = "phone";
    String TIER = "tier";
    // STATUS, VERSION 继承自 CommonDBConst
    String AVATAR = "avatar";
    String JOINED_AT = "joined_at";
    public static final String DELETED_AT = "deleted_at";
    String ANONYMIZED = "anonymized";
    String ANONYMIZED_AT = "anonymized_at";
}
