package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 user_session（消费端会话）。对应 identity-ddl.sql 表 4。
 * 约束: RM-030~037、MAP-003（隐藏 token_id/refresh_token_id）、乐观锁 version（FLOW-04 滑动续期）。
 */
@Data
@TableName("user_session")
public class UserSessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;

    /** access JWT jti（uk_session_token_id） */
    private String tokenId;

    private String refreshTokenId;

    private OffsetDateTime accessExpiresAt;

    private OffsetDateTime refreshExpiresAt;

    private String device;

    private String browser;

    private String ip;

    private String location;

    private Boolean isNewDevice;

    /** method: email/google/apple（ck_session_method） */
    private String method;

    /** status: active/revoked（ck_session_status） */
    private String status;

    private OffsetDateTime lastActiveAt;

    @Version
    private Integer version;

    private OffsetDateTime createdAt;
}
