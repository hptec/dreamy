package com.dreamy.domain.tag.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** tag_dimension 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-7 */
public interface TagDimensionDBConst extends CatalogCommonDBConst {

    String TABLE = "tag_dimension";

    String DESCRIPTION = "description";
    public static final String DELETED_AT = "deleted_at";
}
