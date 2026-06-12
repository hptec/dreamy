package com.dreamy.domain.subscriber.consts;

import com.dreamy.consts.MarketingCommonDBConst;

/** newsletter_subscriber 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-18 */
public interface NewsletterSubscriberDBConst extends MarketingCommonDBConst {

    String TABLE = "newsletter_subscriber";

    String EMAIL = "email";
    String SOURCE = "source";
    String SUBSCRIBED_AT = "subscribed_at";
}
