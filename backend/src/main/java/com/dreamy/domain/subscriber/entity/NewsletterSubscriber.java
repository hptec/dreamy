package com.dreamy.domain.subscriber.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.NewsletterSource;
import com.dreamy.domain.subscriber.consts.NewsletterSubscriberDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 newsletter_subscriber（Newsletter 订阅，仅落表不发码不发邮件——决策 26）。
 * email 小写归一唯一（CV-MKT-008，幂等判重首写胜出）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-18 / IDX-MKT-021 / MKT-IMPL-ENTITY-EXTRA。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "newsletter_subscriber", comment = "Newsletter 订阅（仅落表，不发码不发邮件）", indexes = {
        @Index(name = "uk_newsletter_email", columns = {"email"}, unique = true, local = false)
})
@TableName(value = "newsletter_subscriber", autoResultMap = true)
public class NewsletterSubscriber extends LongAuditableEntity {

    @Column(name = NewsletterSubscriberDBConst.EMAIL, definition = "varchar(255) NOT NULL COMMENT '小写归一，唯一（幂等判重）'")
    private String email;

    @Column(name = NewsletterSubscriberDBConst.SOURCE, definition = "varchar(16) NOT NULL COMMENT 'footer|modal|exit_intent'")
    private NewsletterSource source;

    @Column(name = NewsletterSubscriberDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'en|es|fr'")
    private String locale;

    @Column(name = NewsletterSubscriberDBConst.SUBSCRIBED_AT, definition = "datetime(3) NOT NULL COMMENT '订阅时间'")
    private LocalDateTime subscribedAt;
}
