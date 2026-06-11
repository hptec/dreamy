package com.dreamy.trading.domain.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.trading.domain.payment.consts.ProcessedEventDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 processed_event（Stripe webhook event_id 幂等存储——BE-DIM-4 / webhook 安全第 2 条）。
 * 保留 90 天，SCHED-TRD-002 每日清理（error-strategy 强制项）。
 * 落表与业务变更同事务（TX-TRD-002，失败一并回滚保证 Stripe 可重投）。
 * L2 TRACE: trading-data-detail §9 DDL-12 / IDX-TRD-006/007 / RM-TRD-100/101。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "processed_event", comment = "webhook 幂等消费记录；保留 90 天，SCHED-TRD-002 每日清理", indexes = {
        @Index(name = "uk_event_id", columns = {"event_id"}, unique = true, local = false),
        @Index(name = "idx_event_received", columns = {"received_at"}, unique = false, local = false)
})
@TableName(value = "processed_event", autoResultMap = true)
public class ProcessedEvent extends LongAuditableEntity {

    @Column(name = ProcessedEventDBConst.EVENT_ID, definition = "varchar(64) NOT NULL COMMENT 'Stripe Event id（evt_...）'")
    private String eventId;

    @Column(name = ProcessedEventDBConst.EVENT_TYPE, definition = "varchar(64) NOT NULL COMMENT 'payment_intent.succeeded 等'")
    private String eventType;

    @Column(name = ProcessedEventDBConst.RECEIVED_AT, definition = "datetime(3) NOT NULL COMMENT '接收时刻（90 天清理扫描列）'")
    private LocalDateTime receivedAt;
}
