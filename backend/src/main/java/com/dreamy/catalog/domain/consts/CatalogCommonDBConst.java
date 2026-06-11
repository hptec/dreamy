package com.dreamy.catalog.domain.consts;

/**
 * catalog 域公共数据库列名常量（CP-015 范式根接口）。
 * 各实体 DBConst extends 本接口；跨表共享语义列统一在此定义，消除硬编码列名。
 */
public interface CatalogCommonDBConst {

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    /** 状态列（product/tag 各自枚举语义） */
    String STATUS = "status";

    /** 排序列（category/product/product_image 共享语义） */
    String SORT = "sort";

    /** 多语言附表 locale 列（es|fr，决策 13） */
    String LOCALE = "locale";

    /** 名称列（EN 基准） */
    String NAME = "name";

    /** 商品外键列（六张子表共享，CP-010 逻辑外键） */
    String PRODUCT_ID = "product_id";

    /** 乐观锁版本列（sku） */
    String VERSION = "version";
}
