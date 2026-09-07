package com.dreamy.domain.outbox.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.outbox.consts.EventOutboxDBConst;
import com.dreamy.enums.OutboxStatus;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 event_outbox（事务性发件箱——order-flow-complete §4.4）。
 * 业务事务内插入 PENDING → afterCommit 立即尝试投递 → 成功 SENT；失败保留 PENDING + last_error，
 * OutboxRelayScheduler 按 next_attempt_at 指数退避重投（1m/5m/15m/1h，最多 8 次）→ DEAD + [ALERT]。
 * 运维重放：UPDATE event_outbox SET status=1, next_attempt_at=NOW(3) WHERE status=3。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "event_outbox", comment = "领域事件事务性发件箱", indexes = {
        @Index(name = "idx_outbox_status_next", columns = {"status", "next_attempt_at"}, unique = false, local = false),
        @Index(name = "uk_outbox_event_id", columns = {"event_id"}, unique = true, local = false)
})
@TableName(value = "event_outbox", autoResultMap = true)
public class EventOutbox extends LongAuditableEntity {

    /** 一行一个稳定 event_id：afterCommit 即时投递与 relay 重投复用同一 id，消费端 uk_event_id 去重才有效 */
    @Column(name = EventOutboxDBConst.EVENT_ID, definition = "varchar(36) NULL COMMENT '稳定事件 id（重投复用，消费端幂等键；存量行 NULL 时投递自动生成）'")
    private String eventId;

    @Column(name = EventOutboxDBConst.EVENT_TYPE, definition = "varchar(64) NOT NULL COMMENT '事件类型（= routing key 语义）'")
    private String eventType;

    @Column(name = EventOutboxDBConst.ROUTING_KEY, definition = "varchar(64) NOT NULL COMMENT 'topic routing key，如 order.paid'")
    private String routingKey;

    @Column(name = EventOutboxDBConst.PAYLOAD, definition = "json NOT NULL COMMENT '事件载荷 JSON（snake_case）'")
    private String payload;

    @Column(name = EventOutboxDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '1=PENDING 2=SENT 3=DEAD'")
    private OutboxStatus status;

    @Column(name = EventOutboxDBConst.ATTEMPTS, definition = "int NOT NULL DEFAULT 0 COMMENT '已尝试投递次数'")
    private Integer attempts;

    @Column(name = EventOutboxDBConst.NEXT_ATTEMPT_AT, definition = "datetime(3) NOT NULL COMMENT '下次可投递时刻（relay 扫描列）'")
    private LocalDateTime nextAttemptAt;

    @Column(name = EventOutboxDBConst.LAST_ERROR, definition = "varchar(255) NULL")
    private String lastError;

    @Column(name = EventOutboxDBConst.SENT_AT, definition = "datetime(3) NULL")
    private LocalDateTime sentAt;
}
