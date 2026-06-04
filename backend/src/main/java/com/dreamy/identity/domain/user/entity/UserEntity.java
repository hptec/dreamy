package com.dreamy.identity.domain.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dreamy.identity.domain.user.consts.UserDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 user（自然人账户 / 聚合根）。
 * 约束: RM-001~007、MAP-001、CV-003、乐观锁 version（TX-001/005/006，EC-001）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "user", comment = "自然人账户", indexes = {
        @Index(name = "uk_user_email", columns = {"email"}, unique = true)
})
@TableName(value = "`user`", autoResultMap = true)
public class UserEntity extends LongAuditableEntity {

    @Column(name = UserDBConst.EMAIL, definition = "varchar(255) NOT NULL COMMENT '邮箱'")
    private String email;

    @Column(name = UserDBConst.EMAIL_VERIFIED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '邮箱已验证'")
    private Boolean emailVerified;

    @Column(name = UserDBConst.NAME, definition = "varchar(100) NULL COMMENT '昵称'")
    private String name;

    @Column(name = UserDBConst.PHONE, definition = "varchar(32) NULL COMMENT '手机号'")
    private String phone;

    /** tier: vip/regular（ck_user_tier） */
    @Column(name = UserDBConst.TIER, definition = "varchar(16) NOT NULL DEFAULT 'regular' COMMENT '等级 vip/regular'")
    private String tier;

    /** status: active/disabled/deleted/anonymized（ck_user_status） */
    @Column(name = UserDBConst.STATUS, definition = "varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态 active/disabled/deleted/anonymized'")
    private String status;

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
