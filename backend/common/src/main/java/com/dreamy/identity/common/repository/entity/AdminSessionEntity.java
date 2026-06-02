package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 admin_session（后台会话，access 8h 无 refresh）。对应 identity-ddl.sql 表 10。
 * 约束: RM-090/091、禁用级联 revoke（FLOW-10）。
 */
@Data
@TableName("admin_session")
public class AdminSessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String adminId;

    /** JWT jti（uk_admin_session_token） */
    private String tokenId;

    private String ip;

    private String device;

    /** status: active/revoked（ck_admin_session_status） */
    private String status;

    private OffsetDateTime lastActiveAt;

    private OffsetDateTime createdAt;
}
