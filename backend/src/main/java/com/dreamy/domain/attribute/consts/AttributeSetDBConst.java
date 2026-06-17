package com.dreamy.domain.attribute.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** attribute_set 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-5 */
public interface AttributeSetDBConst extends CatalogCommonDBConst {

    String TABLE = "attribute_set";

    String LABEL = "label";
    public static final String DELETED_AT = "deleted_at";
}
