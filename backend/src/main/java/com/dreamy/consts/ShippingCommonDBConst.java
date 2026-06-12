package com.dreamy.consts;

/**
 * shipping 域公共数据库列名常量（CP-015）。
 * 基类审计列 + 跨表共享字段；表名 carrier/shipping_rate 无 MySQL 保留字（shipping-data-detail §8.4①）。
 */
public interface ShippingCommonDBConst {

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    /** 状态列：carrier.status（enabled/disabled，CP-003） */
    String STATUS = "status";
}
