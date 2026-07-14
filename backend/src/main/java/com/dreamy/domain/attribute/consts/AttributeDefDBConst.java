package com.dreamy.domain.attribute.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** attribute_def 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-3 */
public interface AttributeDefDBConst extends CatalogCommonDBConst {

    String TABLE = "attribute_def";

    /** SQL 保留字，常量存裸名（CP-015），DML 转义由 @TableField 负责 */
    String KEY = "key";
    String LABEL = "label";
    String TYPE = "type";
    String OPTIONS = "options";
}
