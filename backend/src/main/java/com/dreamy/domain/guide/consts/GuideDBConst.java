package com.dreamy.domain.guide.consts;

import com.dreamy.consts.MarketingCommonDBConst;

/** guide 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-11 */
public interface GuideDBConst extends MarketingCommonDBConst {

    String TABLE = "guide";

    String PHASE = "phase";
    String TIMEFRAME = "timeframe";
    String TASKS_COUNT = "tasks_count";
    public static final String DELETED_AT = "deleted_at";
}
