package com.dreamy.identity.domain.authconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.dreamy.identity.domain.authconfig.consts.EmailTemplateDBConst;

/**
 * 表 email_template（邮件模板，三语 × 4 类）。对应 identity-ddl.sql 表 13。
 * 约束: RM-120（uk_template_code_locale，缺失回退默认 locale）、I18N-PLAN。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "email_template", comment = "邮件模板（三语 × 4 类）", indexes = {
        @Index(name = "uk_template_code_locale", columns = {"code", "locale"}, unique = true)
})
@TableName(value = "email_template", autoResultMap = true)
public class EmailTemplate extends LongAuditableEntity {

    /** code: otp/new_device/change_primary/account_deleted（ck_tpl_code） */
    @Column(name = EmailTemplateDBConst.CODE, definition = "varchar(32) NOT NULL COMMENT '模板码 otp/new_device/change_primary/account_deleted'")
    private String code;

    /** locale: en/es/fr（ck_tpl_locale） */
    @Column(name = EmailTemplateDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT '语言 en/es/fr'")
    private String locale;

    @Column(name = EmailTemplateDBConst.SUBJECT, definition = "varchar(255) NOT NULL COMMENT '邮件主题'")
    private String subject;

    @Column(name = EmailTemplateDBConst.BODY, definition = "text NOT NULL COMMENT '邮件正文'")
    private String body;
}
