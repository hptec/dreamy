package com.dreamy.identity.domain.session.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 user_session（消费端会话）。对应 identity-ddl.sql 表 4。
 * 约束: RM-030~037、MAP-003（隐藏 token_id/refresh_token_id）、乐观锁 version（FLOW-04 滑动续期）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "user_session", comment = "消费端会话", indexes = {
        @Index(name = "uk_session_token_id", columns = {"token_id"}, unique = true)
})
@TableName(value = "user_session", autoResultMap = true)
public class UserSessionEntity extends LongAuditableEntity {

    /** 关联用户（FK user.id） */
    @Column(name = "user_id", definition = "bigint NOT NULL COMMENT '关联用户 user.id'")
    private Long userId;

    /** access JWT jti（uk_session_token_id） */
    @Column(name = "token_id", definition = "varchar(64) NOT NULL COMMENT 'access JWT jti'")
    private String tokenId;

    @Column(name = "refresh_token_id", definition = "varchar(64) NULL COMMENT 'refresh JWT jti'")
    private String refreshTokenId;

    @Column(name = "access_expires_at", definition = "datetime NULL COMMENT 'access 过期时间'")
    private LocalDateTime accessExpiresAt;

    @Column(name = "refresh_expires_at", definition = "datetime NULL COMMENT 'refresh 过期时间'")
    private LocalDateTime refreshExpiresAt;

    @Column(name = "device", definition = "varchar(255) NULL COMMENT '设备信息'")
    private String device;

    @Column(name = "browser", definition = "varchar(128) NULL COMMENT '浏览器信息'")
    private String browser;

    @Column(name = "ip", definition = "varchar(64) NULL COMMENT '登录 IP'")
    private String ip;

    @Column(name = "location", definition = "varchar(255) NULL COMMENT '登录地点'")
    private String location;

    @Column(name = "is_new_device", definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否新设备'")
    private Boolean isNewDevice;

    /** method: email/google/apple（ck_session_method） */
    @Column(name = "method", definition = "varchar(16) NOT NULL COMMENT '登录方式 email/google/apple'")
    private String method;

    /** status: active/revoked（ck_session_status） */
    @Column(name = "status", definition = "varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态 active/revoked'")
    private String status;

    @Column(name = "last_active_at", definition = "datetime NULL COMMENT '最近活跃时间'")
    private LocalDateTime lastActiveAt;

    @Version
    @Column(name = "version", definition = "int NOT NULL DEFAULT 0 COMMENT '乐观锁版本'")
    @TableField("version")
    private Integer version;
}
