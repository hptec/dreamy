package com.dreamy.identity.domain.user.repository;

/**
 * user 表列名常量（huihao-mysql 规范）。
 * 含 LongAuditableEntity 基类列 id / created_at / updated_at。
 */
public interface UserDBConst {

    String TABLE = "user";

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

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
