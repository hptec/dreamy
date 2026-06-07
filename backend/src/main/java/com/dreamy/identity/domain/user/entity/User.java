package com.dreamy.identity.domain.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dreamy.identity.domain.enums.UserStatus;
import com.dreamy.identity.domain.enums.UserTier;
import com.dreamy.identity.domain.user.consts.UserDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "user", comment = "自然人账户", indexes = {
        @Index(name = "uk_user_email", columns = {"email"}, unique = true)
})
@TableName(value = "`user`", autoResultMap = true)
public class User extends LongAuditableEntity {

    @Column(name = UserDBConst.EMAIL, definition = "varchar(255) NOT NULL COMMENT '邮箱'")
    private String email;

    @Column(name = UserDBConst.EMAIL_VERIFIED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '邮箱已验证'")
    private Boolean emailVerified;

    @Column(name = UserDBConst.NAME, definition = "varchar(100) NULL COMMENT '昵称'")
    private String name;

    @Column(name = UserDBConst.PHONE, definition = "varchar(32) NULL COMMENT '手机号'")
    private String phone;

    @Column(name = UserDBConst.TIER, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '等级：1=常规 2=VIP'")
    private UserTier tier;

    @Column(name = UserDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=正常 2=已禁用 3=已删除 4=已匿名化'")
    private UserStatus status;

    @Column(name = UserDBConst.AVATAR, definition = "varchar(512) NULL COMMENT '头像 URL'")
    private String avatar;

    @Column(name = UserDBConst.JOINED_AT, definition = "datetime NULL COMMENT '加入时间'")
    private LocalDateTime joinedAt;

    @Column(name = UserDBConst.DELETED_AT, definition = "datetime NULL COMMENT '删除时间'")
    private LocalDateTime deletedAt;

    @Column(name = UserDBConst.ANONYMIZED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '已匿名化'")
    private Boolean anonymized;

    @Column(name = UserDBConst.ANONYMIZED_AT, definition = "datetime NULL COMMENT '匿名化时间'")
    private LocalDateTime anonymizedAt;

    @Version
    @Column(name = UserDBConst.VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '乐观锁版本'")
    @TableField(UserDBConst.VERSION)
    private Integer version;
}
