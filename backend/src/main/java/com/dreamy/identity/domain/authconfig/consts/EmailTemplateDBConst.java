package com.dreamy.identity.domain.authconfig.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * email_template 表列名常量。
 * L2-REF: identity-physical-schema.md § 13 email_template 表结构
 */
public interface EmailTemplateDBConst extends CommonDBConst {

    String TABLE = "email_template";

    String CODE = "code";
    String LOCALE = "locale";
    String SUBJECT = "subject";
    String BODY = "body";
}
