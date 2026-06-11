package com.dreamy.trading.domain.payment.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.trading.domain.payment.entity.ProcessedEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * webhook 幂等存储仓储（RM-TRD-100/101）。
 * insertIgnore 以唯一索引冲突捕获实现（uk_event_id；InnoDB 冲突不毒化事务，与业务变更同事务——TX-TRD-002 ①）。
 */
@Repository
public class ProcessedEventRepository {

    private final ProcessedEventMapper mapper;

    public ProcessedEventRepository(ProcessedEventMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-100 insertIgnore：affected=0 = 已消费（webhook 幂等闸） */
    public int insertIgnore(String eventId, String eventType) {
        ProcessedEvent event = new ProcessedEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setReceivedAt(LocalDateTime.now());
        try {
            return mapper.insert(event);
        } catch (DuplicateKeyException ex) {
            return 0;
        }
    }

    /** RM-TRD-101 deleteBefore：keyset 分批（每批 batchSize，CP-017；SCHED-TRD-002 90 天清理） */
    public int deleteBefore(LocalDateTime cutoff, int batchSize) {
        int totalDeleted = 0;
        while (true) {
            List<ProcessedEvent> batch = mapper.selectList(new LambdaQueryWrapper<ProcessedEvent>()
                    .lt(ProcessedEvent::getReceivedAt, cutoff)
                    .orderByAsc(ProcessedEvent::getId)
                    .select(ProcessedEvent::getId)
                    .last("LIMIT " + batchSize));
            if (batch.isEmpty()) {
                return totalDeleted;
            }
            totalDeleted += mapper.deleteByIds(batch.stream().map(ProcessedEvent::getId).toList());
            if (batch.size() < batchSize) {
                return totalDeleted;
            }
        }
    }
}
