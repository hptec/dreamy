package com.dreamy.catalog.domain.product.consts;

import com.dreamy.catalog.domain.consts.CatalogCommonDBConst;

/** sku 表列名常量。L2 TRACE: catalog-data-detail §9 DDL-14 */
public interface SkuDBConst extends CatalogCommonDBConst {

    String TABLE = "sku";

    String SKU_CODE = "sku_code";
    String COLOR = "color";
    String SIZE = "size";
    String STOCK = "stock";
}
