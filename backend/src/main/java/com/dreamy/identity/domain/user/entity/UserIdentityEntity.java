package com.dreamy.identity.domain.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 user_identity（登录凭证）。对应 identity-ddl.sql 表 2。
 * 约束: RM-010~018、MAP-002（隐藏 provider_uid）、uk_identity_provider_uid（QP-002 幂等核心）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "user_identity", comment = "登录凭证", indexes = {
        @Index(name = "uk_identity_provider_uid", columns = {"provider", "provider_uid"}, unique = true)
})
@TableName(value = "user_identity", autoResultMap = true)
public class UserIdentityEntity extends LongAuditableEntity {

    /** 外键→user.id（非空） */
    @Column(name = "user_id", definition = "bigint NOT NULL COMMENT '外键 user.id'")
    private Long userId;

    /** provider: email/google/apple（ck_identity_provider） */
    @Column(name = "provider", definition = "varchar(16) NOT NULL COMMENT '渠道 email/google/apple'")
    private String provider;

    /** 渠道唯一标识（email=邮箱小写 / OIDC=sub），非 FK 保持 String */
    @Column(name = "provider_uid", definition = "varchar(255) NOT NULL COMMENT '渠道唯一标识 email=邮箱小写/OIDC=sub'")
    private String providerUid;

    @Column(name = "identifier", definition = "varchar(255) NULL COMMENT '展示标识'")
    private String identifier;

    @Column(name = "is_primary", definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否主身份'")
    private Boolean isPrimary;

    @Column(name = "verified", definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已验证'")
    private Boolean verified;

    @Column(name = "connected", definition = "tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否已连接'")
    private Boolean connected;

    @Column(name = "hidden_email", definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否隐藏邮箱 Apple Hide My Email'")
    private Boolean hiddenEmail;

    @Column(name = "relay_email", definition = "varchar(255) NULL COMMENT '中继邮箱'")
    private String relayEmail;

    @Column(name = "relay_valid", definition = "tinyint(1) NULL COMMENT '中继邮箱是否有效'")
    private Boolean relayValid;

    @Column(name = "bound_at", definition = "datetime NULL COMMENT '绑定时间'")
    private LocalDateTime boundAt;

    @Column(name = "last_login_at", definition = "datetime NULL COMMENT '最后登录时间'")
    private LocalDateTime lastLoginAt;
}
