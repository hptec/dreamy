package com.dreamy.domain.banner.consts;

import com.dreamy.consts.MarketingCommonDBConst;

/** banner 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-1 */
public interface BannerDBConst extends MarketingCommonDBConst {

    String TABLE = "banner";

    String IMAGE_URL = "image_url";
    String POSITION = "position";
    String START_TIME = "start_time";
    String END_TIME = "end_time";
    String SORT = "sort";
    String CLICKS = "clicks";
    String SUBTITLE = "subtitle";
    String CTA_TEXT = "cta_text";
    public static final String DELETED_AT = "deleted_at";
}
