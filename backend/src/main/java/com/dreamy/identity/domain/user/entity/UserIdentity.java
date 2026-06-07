package com.dreamy.identity.domain.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.identity.domain.enums.AuthProvider;
import com.dreamy.identity.domain.user.consts.UserIdentityDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "user_identity", comment = "登录凭证", indexes = {
        @Index(name = "uk_identity_provider_uid", columns = {"provider", "provider_uid"}, unique = true)
})
@TableName(value = "user_identity", autoResultMap = true)
public class UserIdentity extends LongAuditableEntity {

    @Column(name = UserIdentityDBConst.USER_ID, definition = "bigint NOT NULL COMMENT '外键 user.id'")
    private Long userId;

    @Column(name = UserIdentityDBConst.PROVIDER, definition = "tinyint NOT NULL COMMENT '渠道：1=邮箱 2=Google 3=Apple'")
    private AuthProvider provider;

    @Column(name = UserIdentityDBConst.PROVIDER_UID, definition = "varchar(255) NOT NULL COMMENT '渠道唯一标识 email=邮箱小写/OIDC=sub'")
    private String providerUid;

    @Column(name = UserIdentityDBConst.IDENTIFIER, definition = "varchar(255) NULL COMMENT '展示标识'")
    private String identifier;

    @Column(name = UserIdentityDBConst.IS_PRIMARY, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否主身份'")
    private Boolean isPrimary;

    @Column(name = UserIdentityDBConst.VERIFIED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已验证'")
    private Boolean verified;

    @Column(name = UserIdentityDBConst.CONNECTED, definition = "tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否已连接'")
    private Boolean connected;

    @Column(name = UserIdentityDBConst.HIDDEN_EMAIL, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否隐藏邮箱 Apple Hide My Email'")
    private Boolean hiddenEmail;

    @Column(name = UserIdentityDBConst.RELAY_EMAIL, definition = "varchar(255) NULL COMMENT '中继邮箱'")
    private String relayEmail;

    @Column(name = UserIdentityDBConst.RELAY_VALID, definition = "tinyint(1) NULL COMMENT '中继邮箱是否有效'")
    private Boolean relayValid;

    @Column(name = UserIdentityDBConst.BOUND_AT, definition = "datetime NULL COMMENT '绑定时间'")
    private LocalDateTime boundAt;

    @Column(name = UserIdentityDBConst.LAST_LOGIN_AT, definition = "datetime NULL COMMENT '最后登录时间'")
    private LocalDateTime lastLoginAt;
}
