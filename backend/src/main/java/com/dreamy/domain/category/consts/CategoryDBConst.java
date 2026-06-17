package com.dreamy.domain.category.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** category 表列名常量（CP-015）。L2 TRACE: catalog-data-detail §9 DDL-1 */
public interface CategoryDBConst extends CatalogCommonDBConst {

    String TABLE = "category";

    String PARENT_ID = "parent_id";
    String LEVEL = "level";
    String ATTRIBUTE_SET_ID = "attribute_set_id";
    String ATTR_OVERRIDES = "attr_overrides";
    public static final String DELETED_AT = "deleted_at";
}
