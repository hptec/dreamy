package com.dreamy.domain.outbox.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.outbox.entity.EventOutbox;
import com.dreamy.enums.OutboxStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发件箱仓储（order-flow-complete §4.4）。状态推进一律条件更新（WHERE status=PENDING），
 * afterCommit 即时投递与 relay 重投并发时互不覆盖。
 */
@Repository
public class EventOutboxRepository {

    private final EventOutboxMapper mapper;

    public EventOutboxRepository(EventOutboxMapper mapper) {
        this.mapper = mapper;
    }

    /** 业务事务内插入（PENDING，attempts=0，next_attempt_at=now） */
    public void insert(EventOutbox row) {
        mapper.insert(row);
    }

    public EventOutbox findById(Long id) {
        return mapper.selectById(id);
    }

    /** relay 扫描：PENDING 且 next_attempt_at ≤ now，id ASC 限批 */
    public List<EventOutbox> listDue(LocalDateTime now, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getStatus, OutboxStatus.PENDING)
                .le(EventOutbox::getNextAttemptAt, now)
                .orderByAsc(EventOutbox::getId)
                .last("LIMIT " + limit));
    }

    /** PENDING → SENT（affected=0 = 已被他处投递） */
    public int markSent(Long id, int attempts, LocalDateTime sentAt) {
        return mapper.update(null, new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, id)
                .eq(EventOutbox::getStatus, OutboxStatus.PENDING)
                .set(EventOutbox::getStatus, OutboxStatus.SENT)
                .set(EventOutbox::getAttempts, attempts)
                .set(EventOutbox::getSentAt, sentAt));
    }

    /** 投递失败：attempts+1 保留 PENDING，写 next_attempt_at/last_error */
    public int markRetry(Long id, int attempts, LocalDateTime nextAttemptAt, String lastError) {
        return mapper.update(null, new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, id)
                .eq(EventOutbox::getStatus, OutboxStatus.PENDING)
                .set(EventOutbox::getAttempts, attempts)
                .set(EventOutbox::getNextAttemptAt, nextAttemptAt)
                .set(EventOutbox::getLastError, lastError));
    }

    /** 超过最大重试 → DEAD */
    public int markDead(Long id, int attempts, String lastError) {
        return mapper.update(null, new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, id)
                .eq(EventOutbox::getStatus, OutboxStatus.PENDING)
                .set(EventOutbox::getStatus, OutboxStatus.DEAD)
                .set(EventOutbox::getAttempts, attempts)
                .set(EventOutbox::getLastError, lastError));
    }
}
