package com.dreamy.infra.mail;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 mail_record（交易/Showroom 邮件发送记录，决策 16/20.5 / FLOW-P11 / FUNC-016/019）。
 * - uk_mail_record_event：event_id 幂等键唯一索引（showroom-data-detail 161 定稿：按 event_id 唯一——
 *   每次 assign 换 email / remind 重发均为独立业务发送，订单类事件 event_id 同样每事件唯一，统一口径；
 *   MQ 重投同 event_id 不重发，bs-671）。
 * - status 状态机 pending→sent / pending→failed(retry_count+1)→dead（MailStatus）。
 * - payload 留存事件载荷 JSON（dead 人工补发渲染依据；invite_token 等敏感值仅落库不入日志）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "mail_record", comment = "邮件发送记录（MQ 消费幂等防重发 + 失败重试，FLOW-P11）", indexes = {
        @Index(name = "uk_mail_record_event", columns = {"event_id"}, unique = true, local = false),
        @Index(name = "idx_mail_record_status_created", columns = {"status", "created_at"},
                unique = false, local = false)
})
@TableName(value = "mail_record", autoResultMap = true)
public class MailRecord extends LongAuditableEntity {

    @Column(name = MailRecordDBConst.TYPE,
            definition = "varchar(32) NOT NULL COMMENT '邮件类型 order_confirmed|order_shipped|refund_resolved|showroom_invite|showroom_assign|showroom_remind（决策 20.5 扩展枚举）'")
    private MailType type;

    @Column(name = MailRecordDBConst.RECIPIENT,
            definition = "varchar(254) NOT NULL COMMENT '收件邮箱（订单类经 CustomerEmailPort 解析；showroom 类取 payload.email）'")
    private String recipient;

    @Column(name = MailRecordDBConst.LOCALE,
            definition = "varchar(8) NOT NULL DEFAULT 'en' COMMENT '渲染语言 en/es/fr（取事件 payload.locale，缺省 en）'")
    private String locale;

    @Column(name = MailRecordDBConst.PAYLOAD,
            definition = "json NULL COMMENT '事件载荷 JSON 快照（dead 人工补发渲染依据）'")
    private String payload;

    @Column(name = MailRecordDBConst.STATUS,
            definition = "varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|sent|failed|dead（MailStatus 状态机）'")
    private MailStatus status;

    @Column(name = MailRecordDBConst.RETRY_COUNT,
            definition = "int NOT NULL DEFAULT 0 COMMENT '失败重试计数（超 dreamy.mq.max-retries=3 → dead）'")
    private Integer retryCount;

    @Column(name = MailRecordDBConst.EVENT_ID,
            definition = "varchar(64) NOT NULL COMMENT '领域事件 event_id（UUID，消费幂等键，uk_mail_record_event）'")
    private String eventId;

    @Column(name = MailRecordDBConst.SENT_AT,
            definition = "datetime(3) NULL COMMENT '发送成功时间（status=sent 同写）'")
    private LocalDateTime sentAt;
}
