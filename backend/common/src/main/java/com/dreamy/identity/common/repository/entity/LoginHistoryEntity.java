package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 表 login_history（登录记录，追加型，1 年保留）。对应 identity-ddl.sql 表 5。
 * 约束: RM-040~043、is_new_device 判定（FLOW-14）、notified 唯一可更新字段。
 */
@Data
@TableName("login_history")
public class LoginHistoryEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 弱引用→user.id，可空（audit_weak_ref） */
    private String userId;

    private String email;

    /** method: email/google/apple（ck_login_method） */
    private String method;

    private String ip;

    private String device;

    private String location;

    /** result: success/failed（ck_login_result） */
    private String result;

    private Boolean isNewDevice;

    private Boolean notified;

    private OffsetDateTime createdAt;
}
