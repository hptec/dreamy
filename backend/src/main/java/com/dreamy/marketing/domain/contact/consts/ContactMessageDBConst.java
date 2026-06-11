package com.dreamy.marketing.domain.contact.consts;

import com.dreamy.marketing.domain.consts.MarketingCommonDBConst;

/** contact_message 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-19 */
public interface ContactMessageDBConst extends MarketingCommonDBConst {

    String TABLE = "contact_message";

    String EMAIL = "email";
    String SUBJECT = "subject";
    String MESSAGE = "message";
    String SUBMITTED_AT = "submitted_at";
}
