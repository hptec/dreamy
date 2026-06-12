package com.dreamy.domain.refund.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** refund 表列名常量。L2 TRACE: trading-data-detail §9 DDL-7 */
public interface RefundDBConst extends TradingCommonDBConst {

    String TABLE = "refund";

    String REFUND_NO = "refund_no";
    String REASON = "reason";
    String REJECT_REASON = "reject_reason";
    String STRIPE_REFUND_ID = "stripe_refund_id";
    String RETURN_TRACKING_NO = "return_tracking_no";
    String APPLIED_AT = "applied_at";
}
