package com.dreamy.identity.domain.audit.repository;

/**
 * operation_log 表列名常量（huihao-mysql 规范）。
 * 含 LongAuditableEntity 基类列 id / created_at / updated_at。
 */
public interface OperationLogDBConst {

    String TABLE = "operation_log";

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    String OPERATOR_ID = "operator_id";
    String OPERATOR_NAME = "operator_name";
    String ACTION = "action";
    String TARGET = "target";
    String IP = "ip";
    String USER_AGENT = "user_agent";
    String CHANGES = "changes";
}
