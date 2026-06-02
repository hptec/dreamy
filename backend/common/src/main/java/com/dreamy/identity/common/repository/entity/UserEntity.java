package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 user（自然人账户 / 聚合根）。对应 identity-ddl.sql 表 1。
 * 约束: RM-001~007、MAP-001、CV-003、乐观锁 version（TX-001/005/006，EC-001）。
 */
@Data
@TableName("`user`")
public class UserEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String email;

    private Boolean emailVerified;

    private String name;

    private String phone;

    /** tier: vip/regular（ck_user_tier） */
    private String tier;

    /** status: active/disabled/deleted/anonymized（ck_user_status） */
    private String status;

    private String avatar;

    private OffsetDateTime joinedAt;

    private OffsetDateTime deletedAt;

    private Boolean anonymized;

    private OffsetDateTime anonymizedAt;

    @Version
    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
