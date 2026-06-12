package com.dreamy.domain.banner.consts;

import com.dreamy.consts.MarketingCommonDBConst;

/** banner_translation 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-2 */
public interface BannerTranslationDBConst extends MarketingCommonDBConst {

    String TABLE = "banner_translation";

    String BANNER_ID = "banner_id";
    String SUBTITLE = "subtitle";
    String CTA_TEXT = "cta_text";
}
