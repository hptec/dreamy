package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 admin_user（后台操作员 / 聚合根）。对应 identity-ddl.sql 表 6。
 * 约束: RM-050~053、MAP-004（隐藏 password_hash）、CV-004 BCrypt、乐观锁 version。
 */
@Data
@TableName("admin_user")
public class AdminUserEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    /** 登录邮箱（唯一 uk_admin_email，创建后不可改） */
    private String email;

    /** BCrypt 密码哈希（redaction.fully_redacted） */
    private String passwordHash;

    private String roleId;

    /** status: active/disabled（ck_admin_status） */
    private String status;

    private OffsetDateTime lastLoginAt;

    @Version
    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
