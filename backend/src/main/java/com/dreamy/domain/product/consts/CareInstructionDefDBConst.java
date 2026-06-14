package com.dreamy.domain.product.consts;

import com.dreamy.consts.CatalogCommonDBConst;

/** care_instruction_def 表列名常量。L2 TRACE: catalog-fabric-care-data-detail §9 DDL */
public interface CareInstructionDefDBConst extends CatalogCommonDBConst {

    String TABLE = "care_instruction_def";

    String CODE = "code";
    String SYMBOL_UNICODE = "symbol_unicode";
    String LABEL_EN = "label_en";
    String LABEL_ZH = "label_zh";
    String CATEGORY = "category";
    String SORT_ORDER = "sort_order";
}
