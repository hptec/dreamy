package com.dreamy.domain.product.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** product_translation 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-12 */
public interface ProductTranslationDBConst extends CatalogCommonDBConst {

    String TABLE = "product_translation";

    String DESCRIPTION = "description";
    /** 设计师备注三语独立列（决策12 / FUNC-017，V20260616 增量加列） */
    String DESIGNER_NOTE = "designer_note";
    String SELLING_POINTS = "selling_points";
    String SEO_TITLE = "seo_title";
    String SEO_DESCRIPTION = "seo_description";
}
