package com.dreamy.domain.audit.consts;

import com.dreamy.consts.CommonDBConst;

/**
 * operation_log 表列名常量。
 * L2-REF: identity-physical-schema.md § 11 operation_log 表结构
 */
public interface OperationLogDBConst extends CommonDBConst {

    String TABLE = "operation_log";

    String OPERATOR_ID = "operator_id";
    String OPERATOR_NAME = "operator_name";
    String ACTION = "action";
    String TARGET = "target";
    String IP = "ip";
    String USER_AGENT = "user_agent";
    String CHANGES = "changes";
}
