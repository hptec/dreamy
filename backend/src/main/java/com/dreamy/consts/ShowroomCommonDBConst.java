package com.dreamy.consts;

/**
 * showroom 域公共数据库列名常量（CP-015 范式根接口）。
 * 各实体 DBConst extends 本接口；跨表共享语义列统一在此定义，消除硬编码列名。
 * L2 TRACE: showroom-data-detail §1.1 注解范式。
 */
public interface ShowroomCommonDBConst {

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    /** 协作空间外键列（showroom_item/showroom_member 共享，CP-010 逻辑外键） */
    String SHOWROOM_ID = "showroom_id";

    /** 款式外键列（showroom_vote/showroom_comment 共享） */
    String SHOWROOM_ITEM_ID = "showroom_item_id";

    /** 成员外键列（vote/comment 的去重身份，仅取鉴权主体解析结果，CV-SHR-006） */
    String MEMBER_ID = "member_id";
}
