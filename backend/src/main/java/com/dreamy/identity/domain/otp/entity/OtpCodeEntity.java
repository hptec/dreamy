package com.dreamy.identity.domain.otp.entity;

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
import com.dreamy.identity.domain.otp.consts.OtpCodeDBConst;

/**
 * 表 otp_code（一次性验证码，仅存哈希）。对应 identity-ddl.sql 表 3。
 * 约束: RM-020~025、CV-004（仅 code_hash）、乐观锁 version（防并发绕过 attempts，TX-001）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "otp_code", comment = "一次性验证码", indexes = {
        @Index(name = "idx_otp_email", columns = {"email"})
})
@TableName(value = "otp_code", autoResultMap = true)
public class OtpCodeEntity extends LongAuditableEntity {

    @Column(name = OtpCodeDBConst.EMAIL, definition = "varchar(255) NOT NULL COMMENT '邮箱'")
    private String email;

    /** OTP 哈希（绝不存明文，redaction.fully_redacted） */
    @Column(name = OtpCodeDBConst.CODE_HASH, definition = "varchar(255) NOT NULL COMMENT 'OTP 哈希（仅哈希）'")
    private String codeHash;

    /** 验证码长度 4/6/8（ck_otp_length） */
    @Column(name = OtpCodeDBConst.LENGTH, definition = "int NOT NULL COMMENT '验证码长度 4/6/8'")
    private Integer length;

    @Column(name = OtpCodeDBConst.EXPIRES_AT, definition = "datetime NOT NULL COMMENT '过期时间'")
    private LocalDateTime expiresAt;

    @Column(name = OtpCodeDBConst.ATTEMPTS, definition = "int NOT NULL DEFAULT 0 COMMENT '已尝试次数'")
    private Integer attempts;

    /** 最大尝试次数 3..10（ck_otp_max） */
    @Column(name = OtpCodeDBConst.MAX_ATTEMPTS, definition = "int NOT NULL COMMENT '最大尝试次数 3..10'")
    private Integer maxAttempts;

    /** status: pending/consumed/expired/locked（ck_otp_status） */
    @Column(name = OtpCodeDBConst.STATUS, definition = "varchar(16) NOT NULL DEFAULT 'pending' COMMENT '状态 pending/consumed/expired/locked'")
    private String status;

    @Column(name = OtpCodeDBConst.LAST_SENT_AT, definition = "datetime NULL COMMENT '最近发送时间'")
    private LocalDateTime lastSentAt;

    @Version
    @Column(name = OtpCodeDBConst.VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '乐观锁版本'")
    @TableField("version")
    private Integer version;
}
