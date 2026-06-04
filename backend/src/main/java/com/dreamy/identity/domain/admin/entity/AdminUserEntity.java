package com.dreamy.identity.domain.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dreamy.identity.domain.admin.consts.AdminUserDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 admin_user（后台操作员 / 聚合根）。对应 identity-ddl.sql 表 6。
 * 约束: RM-050~053、MAP-004（隐藏 password_hash）、CV-004 BCrypt、乐观锁 version。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "admin_user", comment = "后台操作员", indexes = {
        @Index(name = "uk_admin_email", columns = {"email"}, unique = true)
})
@TableName(value = "admin_user", autoResultMap = true)
public class AdminUserEntity extends LongAuditableEntity {

    @Column(name = AdminUserDBConst.NAME, definition = "varchar(100) NULL COMMENT '操作员名称'")
    private String name;

    /** 登录邮箱（唯一 uk_admin_email，创建后不可改） */
    @Column(name = AdminUserDBConst.EMAIL, definition = "varchar(255) NOT NULL COMMENT '登录邮箱'")
    private String email;

    /** BCrypt 密码哈希（redaction.fully_redacted） */
    @Column(name = AdminUserDBConst.PASSWORD_HASH, definition = "varchar(255) NOT NULL COMMENT 'BCrypt 密码哈希'")
    private String passwordHash;

    /** 关联角色（FK role.id） */
    @Column(name = AdminUserDBConst.ROLE_ID, definition = "bigint NOT NULL COMMENT '关联角色 role.id'")
    private Long roleId;

    /** status: active/disabled（ck_admin_status） */
    @Column(name = AdminUserDBConst.STATUS, definition = "varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态 active/disabled'")
    private String status;

    @Column(name = AdminUserDBConst.LAST_LOGIN_AT, definition = "datetime NULL COMMENT '最近登录时间'")
    private LocalDateTime lastLoginAt;

    @Version
    @Column(name = AdminUserDBConst.VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '乐观锁版本'")
    @TableField(AdminUserDBConst.VERSION)
    private Integer version;
}
