package com.dreamy.review.domain.question.consts;

import com.dreamy.review.domain.consts.ReviewCommonDBConst;

/** product_question 表列名常量。L2 TRACE: review-data-detail §9 DDL-3 */
public interface ProductQuestionDBConst extends ReviewCommonDBConst {

    String TABLE = "product_question";

    String ASKER = "asker";
    String QUESTION = "question";
    String ASKED_AT = "asked_at";
    String ANSWER = "answer";
    String ANSWER_TIME = "answer_time";
    String VISIBLE = "visible";
}
