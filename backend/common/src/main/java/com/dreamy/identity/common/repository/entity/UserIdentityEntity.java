package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 user_identity（登录凭证）。对应 identity-ddl.sql 表 2。
 * 约束: RM-010~018、MAP-002（隐藏 provider_uid）、uk_identity_provider_uid（QP-002 幂等核心）。
 */
@Data
@TableName("user_identity")
public class UserIdentityEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;

    /** provider: email/google/apple（ck_identity_provider） */
    private String provider;

    /** 渠道唯一标识（email=邮箱小写 / OIDC=sub） */
    private String providerUid;

    private String identifier;

    private Boolean isPrimary;

    private Boolean verified;

    private Boolean connected;

    private Boolean hiddenEmail;

    private String relayEmail;

    private Boolean relayValid;

    private OffsetDateTime boundAt;

    private OffsetDateTime lastLoginAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
