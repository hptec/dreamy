package com.dreamy.consts;

/**
 * review 域公共数据库列名常量（CP-015 范式根接口）。
 * 各实体 DBConst extends 本接口；跨表共享语义列统一在此定义，消除硬编码列名。
 */
public interface ReviewCommonDBConst {

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    /** 商品外键列（review/product_question 共享，CP-010 逻辑外键） */
    String PRODUCT_ID = "product_id";

    /** 提交者外键列（BE-DIM-6 强隔离，JWT subject 落库） */
    String USER_ID = "user_id";

    /** 状态列（review.status 三态枚举语义） */
    String STATUS = "status";
}
