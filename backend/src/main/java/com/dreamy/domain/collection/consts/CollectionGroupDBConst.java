package com.dreamy.domain.collection.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** collection_group 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-7 */
public interface CollectionGroupDBConst extends CatalogCommonDBConst {

    String TABLE = "collection_group";

    String DESCRIPTION = "description";
    public static final String DELETED_AT = "deleted_at";
}
