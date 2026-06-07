package com.dreamy.identity.domain.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.identity.domain.audit.consts.LoginHistoryDBConst;
import com.dreamy.identity.domain.enums.AuthProvider;
import com.dreamy.identity.domain.enums.LoginOutcome;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "login_history", comment = "登录记录", indexes = {
        @Index(name = "idx_login_history_user", columns = {"user_id"}, unique = false)
})
@TableName(value = "login_history", autoResultMap = true)
public class LoginHistory extends LongAuditableEntity {

    @Column(name = LoginHistoryDBConst.USER_ID, definition = "bigint NULL COMMENT '弱引用 user.id，可空'")
    private Long userId;

    @Column(name = LoginHistoryDBConst.EMAIL, definition = "varchar(255) NULL COMMENT '登录邮箱'")
    private String email;

    @Column(name = LoginHistoryDBConst.METHOD, definition = "tinyint NOT NULL COMMENT '登录方式：1=邮箱 2=Google 3=Apple'")
    private AuthProvider method;

    @Column(name = LoginHistoryDBConst.IP, definition = "varchar(64) NULL COMMENT '登录 IP'")
    private String ip;

    @Column(name = LoginHistoryDBConst.DEVICE, definition = "varchar(255) NULL COMMENT '设备信息'")
    private String device;

    @Column(name = LoginHistoryDBConst.LOCATION, definition = "varchar(255) NULL COMMENT '登录地点'")
    private String location;

    @Column(name = LoginHistoryDBConst.RESULT, definition = "tinyint NOT NULL COMMENT '登录结果：1=成功 2=失败'")
    private LoginOutcome result;

    @Column(name = LoginHistoryDBConst.IS_NEW_DEVICE, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否新设备'")
    private Boolean isNewDevice;

    @Column(name = LoginHistoryDBConst.NOTIFIED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已通知'")
    private Boolean notified;
}
