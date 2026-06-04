package com.dreamy.identity.domain.user.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * user 表列名常量（huihao-mysql 规范）。
 * L2-REF: identity-physical-schema.md § 1 user 表结构
 * 决策 3：迁包 repository/ → consts/，消除 dead code（@Column name 引用常量）。
 */
public interface UserDBConst extends CommonDBConst {

    String TABLE = "user";

    String EMAIL = "email";
    String EMAIL_VERIFIED = "email_verified";
    String NAME = "name";
    String PHONE = "phone";
    String TIER = "tier";
    String STATUS = "status";
    String AVATAR = "avatar";
    String JOINED_AT = "joined_at";
    String DELETED_AT = "deleted_at";
    String ANONYMIZED = "anonymized";
    String ANONYMIZED_AT = "anonymized_at";
    String VERSION = "version";
}
