package com.dreamy.catalog.domain.product.consts;

import com.dreamy.catalog.domain.consts.CatalogCommonDBConst;

/** product 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-11 */
public interface ProductDBConst extends CatalogCommonDBConst {

    String TABLE = "product";

    String SLUG = "slug";
    String SUBTITLE = "subtitle";
    String CATEGORY_ID = "category_id";
    String PRODUCT_TYPE = "product_type";
    String DESCRIPTION = "description";
    String DESIGNER_NOTE = "designer_note";
    String PRICE = "price";
    String COMPARE_AT = "compare_at";
    String INSTALLMENT = "installment";
    String MULTI_CURRENCY_PRICES = "multi_currency_prices";
    String IS_NEW = "is_new";
    String IS_BEST = "is_best";
    String RECOMMEND = "recommend";
    String LEAD_TIME_DAYS = "lead_time_days";
    String RUSH_AVAILABLE = "rush_available";
    String CUSTOM_SIZE_AVAILABLE = "custom_size_available";
    String SILHOUETTE = "silhouette";
    String NECKLINE = "neckline";
    String SLEEVE = "sleeve";
    String BACK_STYLE = "back_style";
    String WAISTLINE = "waistline";
    String TRAIN = "train";
    String LENGTH = "length";
    String FABRIC = "fabric";
    String FABRIC_COMPOSITION = "fabric_composition";
    String SUPPORT = "support";
    String SEASON = "season";
    String EMBELLISHMENTS = "embellishments";
    String OCCASIONS = "occasions";
    String STYLE_TAGS = "style_tags";
    String MODEL_HEIGHT = "model_height";
    String MODEL_SIZE = "model_size";
    String MODEL_BODY_TYPE = "model_body_type";
    String CARE_INSTRUCTIONS = "care_instructions";
    String COUNTRY_OF_ORIGIN = "country_of_origin";
    String STYLE_NO = "style_no";
    String SEO_TITLE = "seo_title";
    String SEO_DESC = "seo_desc";
    String SALES_30D = "sales_30d";
    String SALES_REFRESHED_AT = "sales_refreshed_at";
    String RATING_AVG = "rating_avg";
    String RATING_COUNT = "rating_count";
}
