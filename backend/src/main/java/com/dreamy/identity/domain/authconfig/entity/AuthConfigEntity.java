package com.dreamy.identity.domain.authconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 auth_config（认证配置 / 单例 id=1）。对应 identity-ddl.sql 表 12。
 * 约束: RM-110/111、CV-002 区间双校验（越界 40002）、email_enabled 恒 true。
 * 单例：固定 id=1（继承 LongAuditableEntity 的 Long id）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "auth_config", comment = "认证配置（单例 id=1）")
@TableName(value = "auth_config", autoResultMap = true)
public class AuthConfigEntity extends LongAuditableEntity {

    @Column(name = "email_enabled", definition = "tinyint(1) NOT NULL DEFAULT 1 COMMENT '邮箱登录启用（恒 true）'")
    private Boolean emailEnabled;

    @Column(name = "google_enabled", definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Google 登录启用'")
    private Boolean googleEnabled;

    @Column(name = "apple_enabled", definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Apple 登录启用'")
    private Boolean appleEnabled;

    /** OTP 长度 4/6/8（ck_cfg_otp_length） */
    @Column(name = "otp_length", definition = "int NOT NULL DEFAULT 6 COMMENT 'OTP 长度 4/6/8'")
    private Integer otpLength;

    /** OTP 有效期 1..30 分钟（ck_cfg_otp_ttl） */
    @Column(name = "otp_ttl_minutes", definition = "int NOT NULL DEFAULT 5 COMMENT 'OTP 有效期 1..30 分钟'")
    private Integer otpTtlMinutes;

    /** 重发间隔 10..120 秒（ck_cfg_otp_resend） */
    @Column(name = "otp_resend_seconds", definition = "int NOT NULL DEFAULT 60 COMMENT '重发间隔 10..120 秒'")
    private Integer otpResendSeconds;

    /** 最大尝试 3..10（ck_cfg_otp_max） */
    @Column(name = "otp_max_attempts", definition = "int NOT NULL DEFAULT 5 COMMENT '最大尝试 3..10'")
    private Integer otpMaxAttempts;

    /** 最少连接方式 1..3（ck_cfg_min_methods） */
    @Column(name = "min_methods", definition = "int NOT NULL DEFAULT 1 COMMENT '最少连接方式 1..3'")
    private Integer minMethods;

    @Column(name = "google_client_id", definition = "varchar(255) NULL COMMENT 'Google Client ID'")
    private String googleClientId;

    @Column(name = "apple_service_id", definition = "varchar(255) NULL COMMENT 'Apple Service ID'")
    private String appleServiceId;
}
