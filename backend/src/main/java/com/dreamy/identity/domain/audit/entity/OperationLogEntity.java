package com.dreamy.identity.domain.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 operation_log（操作日志，追加只读不可删，1-3 年保留）。对应 identity-ddl.sql 表 11。
 * 约束: RM-100~102（无 update/delete 接口，EDGE-018）、MAP-006、FLOW-17 AOP 审计、RI-003 弱引用。
 * 继承 LongAuditableEntity 后追加 updated_at 列（追加型日志可接受）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "operation_log", comment = "操作日志", indexes = {
        @Index(name = "idx_oplog_operator", columns = {"operator_id"}, unique = false)
})
@TableName(value = "operation_log", autoResultMap = true)
public class OperationLogEntity extends LongAuditableEntity {

    /** 弱引用→admin_user.id；系统=NULL（audit_weak_ref） */
    @Column(name = "operator_id", definition = "bigint NULL COMMENT '弱引用 admin_user.id，系统操作为 NULL'")
    private Long operatorId;

    @Column(name = "operator_name", definition = "varchar(100) NULL COMMENT '操作者名称'")
    private String operatorName;

    /** action: 15 种枚举（ck_oplog_action） */
    @Column(name = "action", definition = "varchar(32) NOT NULL COMMENT '操作动作（15 种枚举）'")
    private String action;

    @Column(name = "target", definition = "varchar(255) NULL COMMENT '操作目标'")
    private String target;

    @Column(name = "ip", definition = "varchar(64) NULL COMMENT '操作 IP'")
    private String ip;

    @Column(name = "user_agent", definition = "varchar(512) NULL COMMENT 'User-Agent'")
    private String userAgent;

    /** 变更前后对比 JSON {before,after}（原样存储） */
    @Column(name = "changes", definition = "text NULL COMMENT '变更前后对比 JSON {before,after}'")
    private String changes;
}
