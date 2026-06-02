package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 operation_log（操作日志，追加只读不可删，1-3 年保留）。对应 identity-ddl.sql 表 11。
 * 约束: RM-100~102（无 update/delete 接口，EDGE-018）、MAP-006、FLOW-17 AOP 审计、RI-003 弱引用。
 */
@Data
@TableName("operation_log")
public class OperationLogEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 弱引用→admin_user.id；系统=NULL（audit_weak_ref） */
    private String operatorId;

    private String operatorName;

    /** action: 15 种枚举（ck_oplog_action） */
    private String action;

    private String target;

    private String ip;

    private String userAgent;

    /** 变更前后对比 JSON {before,after}（原样存储） */
    private String changes;

    private OffsetDateTime createdAt;
}
