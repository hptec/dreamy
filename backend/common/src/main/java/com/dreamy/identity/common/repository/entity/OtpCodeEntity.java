package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 otp_code（一次性验证码，仅存哈希）。对应 identity-ddl.sql 表 3。
 * 约束: RM-020~025、CV-004（仅 code_hash）、乐观锁 version（防并发绕过 attempts，TX-001）。
 */
@Data
@TableName("otp_code")
public class OtpCodeEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String email;

    /** OTP 哈希（绝不存明文，redaction.fully_redacted） */
    private String codeHash;

    /** 验证码长度 4/6/8（ck_otp_length） */
    private Integer length;

    private OffsetDateTime expiresAt;

    private Integer attempts;

    /** 最大尝试次数 3..10（ck_otp_max） */
    private Integer maxAttempts;

    /** status: pending/consumed/expired/locked（ck_otp_status） */
    private String status;

    private OffsetDateTime lastSentAt;

    @Version
    private Integer version;

    private OffsetDateTime createdAt;
}
