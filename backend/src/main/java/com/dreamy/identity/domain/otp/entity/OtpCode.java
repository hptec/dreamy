package com.dreamy.identity.domain.otp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dreamy.identity.domain.enums.OtpStatus;
import com.dreamy.identity.domain.otp.consts.OtpCodeDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "otp_code", comment = "一次性验证码", indexes = {
        @Index(name = "idx_otp_email", columns = {"email"})
})
@TableName(value = "otp_code", autoResultMap = true)
public class OtpCode extends LongAuditableEntity {

    @Column(name = OtpCodeDBConst.EMAIL, definition = "varchar(255) NOT NULL COMMENT '邮箱'")
    private String email;

    @Column(name = OtpCodeDBConst.CODE_HASH, definition = "varchar(255) NOT NULL COMMENT 'OTP 哈希（仅哈希）'")
    private String codeHash;

    @Column(name = OtpCodeDBConst.LENGTH, definition = "int NOT NULL COMMENT '验证码长度 4/6/8'")
    private Integer length;

    @Column(name = OtpCodeDBConst.EXPIRES_AT, definition = "datetime NOT NULL COMMENT '过期时间'")
    private LocalDateTime expiresAt;

    @Column(name = OtpCodeDBConst.ATTEMPTS, definition = "int NOT NULL DEFAULT 0 COMMENT '已尝试次数'")
    private Integer attempts;

    @Column(name = OtpCodeDBConst.MAX_ATTEMPTS, definition = "int NOT NULL COMMENT '最大尝试次数 3..10'")
    private Integer maxAttempts;

    @Column(name = OtpCodeDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=待验证 2=已消耗 3=已过期 4=已锁定'")
    private OtpStatus status;

    @Column(name = OtpCodeDBConst.LAST_SENT_AT, definition = "datetime NULL COMMENT '最近发送时间'")
    private LocalDateTime lastSentAt;

    @Version
    @Column(name = OtpCodeDBConst.VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '乐观锁版本'")
    @TableField("version")
    private Integer version;
}
