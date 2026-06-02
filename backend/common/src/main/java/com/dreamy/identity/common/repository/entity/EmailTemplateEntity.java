package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 email_template（邮件模板，三语 × 4 类）。对应 identity-ddl.sql 表 13。
 * 约束: RM-120（uk_template_code_locale，缺失回退默认 locale）、I18N-PLAN。
 */
@Data
@TableName("email_template")
public class EmailTemplateEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    /** code: otp/new_device/change_primary/account_deleted（ck_tpl_code） */
    private String code;

    /** locale: en/es/fr（ck_tpl_locale） */
    private String locale;

    private String subject;

    private String body;

    private OffsetDateTime updatedAt;
}
