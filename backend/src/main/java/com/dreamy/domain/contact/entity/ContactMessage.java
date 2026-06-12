package com.dreamy.domain.contact.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.contact.consts.ContactMessageDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 contact_message（联系表单消息——决策 30，本期无管理端读路径，运营直查库）。
 * 无判重：同人多次留言均落表（E-MKT-12 STEP-MKT-01）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-19 / IDX-MKT-022 / MKT-IMPL-ENTITY-EXTRA。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "contact_message", comment = "联系表单消息（管理端本期不做查看页）", indexes = {
        @Index(name = "idx_contact_submitted", columns = {"submitted_at"}, unique = false, local = false)
})
@TableName(value = "contact_message", autoResultMap = true)
public class ContactMessage extends LongAuditableEntity {

    @Column(name = ContactMessageDBConst.NAME, definition = "varchar(100) NOT NULL")
    private String name;

    @Column(name = ContactMessageDBConst.EMAIL, definition = "varchar(255) NOT NULL")
    private String email;

    @Column(name = ContactMessageDBConst.SUBJECT, definition = "varchar(200) NULL")
    private String subject;

    @Column(name = ContactMessageDBConst.MESSAGE, definition = "varchar(5000) NOT NULL")
    private String message;

    @Column(name = ContactMessageDBConst.SUBMITTED_AT, definition = "datetime(3) NOT NULL COMMENT '提交时间'")
    private LocalDateTime submittedAt;
}
