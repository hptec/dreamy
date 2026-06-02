package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 auth_config（认证配置 / 单例 id=1）。对应 identity-ddl.sql 表 12。
 * 约束: RM-110/111、CV-002 区间双校验（越界 40002）、email_enabled 恒 true。
 */
@Data
@TableName("auth_config")
public class AuthConfigEntity {

    /** 单例主键固定=1（shared-contracts id_field 例外） */
    @TableId(type = IdType.INPUT)
    private Integer id;

    private Boolean emailEnabled;

    private Boolean googleEnabled;

    private Boolean appleEnabled;

    /** OTP 长度 4/6/8（ck_cfg_otp_length） */
    private Integer otpLength;

    /** OTP 有效期 1..30 分钟（ck_cfg_otp_ttl） */
    private Integer otpTtlMinutes;

    /** 重发间隔 10..120 秒（ck_cfg_otp_resend） */
    private Integer otpResendSeconds;

    /** 最大尝试 3..10（ck_cfg_otp_max） */
    private Integer otpMaxAttempts;

    /** 最少连接方式 1..3（ck_cfg_min_methods） */
    private Integer minMethods;

    private String googleClientId;

    private String appleServiceId;

    private OffsetDateTime updatedAt;
}
