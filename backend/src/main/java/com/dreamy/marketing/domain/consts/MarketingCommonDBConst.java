package com.dreamy.marketing.domain.consts;

/**
 * marketing 域公共数据库列名常量（CP-015 范式根接口）。
 * 各实体 DBConst extends 本接口；跨表共享语义列统一在此定义，消除硬编码列名。
 * L2 TRACE: marketing-data-detail §1.1。
 */
public interface MarketingCommonDBConst {

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    /** 状态列（banner/blog/wedding/lookbook/guide/coupon/flash 各自枚举语义） */
    String STATUS = "status";

    /** 多语言附表 locale 列（es|fr，决策 13 + CV-MKT-007） */
    String LOCALE = "locale";

    /** 名称列（EN 基准） */
    String NAME = "name";

    /** 标题列（EN 基准，DEC-MKT-1） */
    String TITLE = "title";

    /** 商品逻辑外键列（三张 nm 关联表共享，CP-010） */
    String PRODUCT_ID = "product_id";

    /** 起止时间列（coupon/flash） */
    String START_AT = "start_at";
    String END_AT = "end_at";

    /** 封面列（blog/wedding） */
    String COVER = "cover";

    /** 描述列（lookbook/coupon EN 基准） */
    String DESCRIPTION = "description";

    /** 正文列（guide EN 基准 / blog_post_translation） */
    String BODY = "body";
}
