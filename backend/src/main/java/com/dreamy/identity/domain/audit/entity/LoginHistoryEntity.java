package com.dreamy.identity.domain.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.dreamy.identity.domain.audit.consts.LoginHistoryDBConst;

/**
 * 表 login_history（登录记录，追加型，1 年保留）。对应 identity-ddl.sql 表 5。
 * 约束: RM-040~043、is_new_device 判定（FLOW-14）、notified 唯一可更新字段。
 * 继承 LongAuditableEntity 后追加 updated_at 列（追加型日志可接受）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "login_history", comment = "登录记录", indexes = {
        @Index(name = "idx_login_history_user", columns = {"user_id"}, unique = false)
})
@TableName(value = "login_history", autoResultMap = true)
public class LoginHistoryEntity extends LongAuditableEntity {

    /** 弱引用→user.id，可空（audit_weak_ref） */
    @Column(name = LoginHistoryDBConst.USER_ID, definition = "bigint NULL COMMENT '弱引用 user.id，可空'")
    private Long userId;

    @Column(name = LoginHistoryDBConst.EMAIL, definition = "varchar(255) NULL COMMENT '登录邮箱'")
    private String email;

    /** method: email/google/apple（ck_login_method） */
    @Column(name = LoginHistoryDBConst.METHOD, definition = "varchar(16) NOT NULL COMMENT '登录方式 email/google/apple'")
    private String method;

    @Column(name = LoginHistoryDBConst.IP, definition = "varchar(64) NULL COMMENT '登录 IP'")
    private String ip;

    @Column(name = LoginHistoryDBConst.DEVICE, definition = "varchar(255) NULL COMMENT '设备信息'")
    private String device;

    @Column(name = LoginHistoryDBConst.LOCATION, definition = "varchar(255) NULL COMMENT '登录地点'")
    private String location;

    /** result: success/failed（ck_login_result） */
    @Column(name = LoginHistoryDBConst.RESULT, definition = "varchar(16) NOT NULL COMMENT '登录结果 success/failed'")
    private String result;

    @Column(name = LoginHistoryDBConst.IS_NEW_DEVICE, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否新设备'")
    private Boolean isNewDevice;

    @Column(name = LoginHistoryDBConst.NOTIFIED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已通知'")
    private Boolean notified;
}
