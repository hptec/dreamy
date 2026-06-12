package com.dreamy.domain.review.consts;

import com.dreamy.consts.ReviewCommonDBConst;

/** review 表列名常量。L2 TRACE: review-data-detail §9 DDL-1 */
public interface ReviewDBConst extends ReviewCommonDBConst {

    String TABLE = "review";

    String CUSTOMER_NAME = "customer_name";
    String RATING = "rating";
    String CONTENT = "content";
    String FEATURED = "featured";
    String SUBMITTED_AT = "submitted_at";
    String REPLY_AUTHOR = "reply_author";
    String REPLY_CONTENT = "reply_content";
    String REPLY_TIME = "reply_time";
}
