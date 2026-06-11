package com.dreamy.review.domain.review.consts;

import com.dreamy.review.domain.consts.ReviewCommonDBConst;

/** review_image 表列名常量。L2 TRACE: review-data-detail §9 DDL-2 */
public interface ReviewImageDBConst extends ReviewCommonDBConst {

    String TABLE = "review_image";

    String REVIEW_ID = "review_id";
    String URL = "url";
    String REJECTED = "rejected";
}
