package com.dreamy.domain.collection.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** collection 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-9 */
public interface CollectionDBConst extends CatalogCommonDBConst {

    String TABLE = "collection";

    String COLLECTION_GROUP_ID = "collection_group_id";
    public static final String DELETED_AT = "deleted_at";
}
