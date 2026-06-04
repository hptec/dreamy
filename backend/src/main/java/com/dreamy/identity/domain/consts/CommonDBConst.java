package com.dreamy.identity.domain.consts;

/**
 * 公共数据库列名常量。
 * 用于所有继承 LongAuditableEntity 的实体的基类列。
 * L2-REF: identity-physical-schema.md § 0.2 公共审计列
 */
public interface CommonDBConst {

    /** 主键列（Long 自增） */
    String ID = "id";

    /** 创建时间列（DATETIME(3)，UTC） */
    String CREATED_AT = "created_at";

    /** 更新时间列（DATETIME(3)，UTC） */
    String UPDATED_AT = "updated_at";
}
