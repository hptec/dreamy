package com.dreamy.identity.domain.session.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.identity.domain.enums.SessionStatus;
import com.dreamy.identity.domain.session.consts.AdminSessionDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "admin_session", comment = "后台会话", indexes = {
        @Index(name = "uk_admin_session_token", columns = {"token_id"}, unique = true)
})
@TableName(value = "admin_session", autoResultMap = true)
public class AdminSession extends LongAuditableEntity {

    @Column(name = AdminSessionDBConst.ADMIN_ID, definition = "bigint NOT NULL COMMENT '关联操作员 admin_user.id'")
    private Long adminId;

    @Column(name = AdminSessionDBConst.TOKEN_ID, definition = "varchar(64) NOT NULL COMMENT 'JWT jti'")
    private String tokenId;

    @Column(name = AdminSessionDBConst.IP, definition = "varchar(64) NULL COMMENT '登录 IP'")
    private String ip;

    @Column(name = AdminSessionDBConst.DEVICE, definition = "varchar(255) NULL COMMENT '设备信息'")
    private String device;

    @Column(name = AdminSessionDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=活跃 2=已撤销'")
    private SessionStatus status;

    @Column(name = AdminSessionDBConst.LAST_ACTIVE_AT, definition = "datetime NULL COMMENT '最近活跃时间'")
    private LocalDateTime lastActiveAt;
}
