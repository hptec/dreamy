package com.dreamy.identity.domain.session.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 admin_session（后台会话，access 8h 无 refresh）。对应 identity-ddl.sql 表 10。
 * 约束: RM-090/091、禁用级联 revoke（FLOW-10）。
 * 注: 旧实体仅 created_at，基类统一补充 updated_at（审计统一，可接受）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "admin_session", comment = "后台会话", indexes = {
        @Index(name = "uk_admin_session_token", columns = {"token_id"}, unique = true)
})
@TableName(value = "admin_session", autoResultMap = true)
public class AdminSessionEntity extends LongAuditableEntity {

    /** 关联后台操作员（FK admin_user.id） */
    @Column(name = "admin_id", definition = "bigint NOT NULL COMMENT '关联操作员 admin_user.id'")
    private Long adminId;

    /** JWT jti（uk_admin_session_token） */
    @Column(name = "token_id", definition = "varchar(64) NOT NULL COMMENT 'JWT jti'")
    private String tokenId;

    @Column(name = "ip", definition = "varchar(64) NULL COMMENT '登录 IP'")
    private String ip;

    @Column(name = "device", definition = "varchar(255) NULL COMMENT '设备信息'")
    private String device;

    /** status: active/revoked（ck_admin_session_status） */
    @Column(name = "status", definition = "varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态 active/revoked'")
    private String status;

    @Column(name = "last_active_at", definition = "datetime NULL COMMENT '最近活跃时间'")
    private LocalDateTime lastActiveAt;
}
