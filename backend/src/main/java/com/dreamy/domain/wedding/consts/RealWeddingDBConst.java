package com.dreamy.domain.wedding.consts;

import com.dreamy.consts.MarketingCommonDBConst;

/** real_wedding 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-5 */
public interface RealWeddingDBConst extends MarketingCommonDBConst {

    String TABLE = "real_wedding";

    String COUPLE = "couple";
    String LOCATION = "location";
    String THEME = "theme";
    String WEDDING_DATE = "wedding_date";
    String STORY = "story";
    public static final String DELETED_AT = "deleted_at";
}
