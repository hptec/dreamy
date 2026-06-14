package com.dreamy.domain.product.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** product_translation 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-12 */
public interface ProductTranslationDBConst extends CatalogCommonDBConst {

    String TABLE = "product_translation";

    String DESCRIPTION = "description";
    String SELLING_POINTS = "selling_points";
    String SEO_TITLE = "seo_title";
    String SEO_DESCRIPTION = "seo_description";
}
