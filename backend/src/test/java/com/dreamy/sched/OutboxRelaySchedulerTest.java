package com.dreamy.sched;

import com.dreamy.domain.outbox.entity.EventOutbox;
import com.dreamy.domain.outbox.repository.EventOutboxRepository;
import com.dreamy.mq.TradingEventsPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发件箱重投调度单测（order-flow-complete §4.4 / §6.3 第 13 条）：锁未获取直接返回；逐条重投一条抛错不阻塞；
 * 空批幂等。
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    RedissonClient redissonClient;
    @Mock
    RLock lock;
    @Mock
    EventOutboxRepository outboxRepository;
    @Mock
    TradingEventsPublisher eventsPublisher;

    @InjectMocks
    OutboxRelayScheduler scheduler;

    private EventOutbox row(long id) {
        EventOutbox row = new EventOutbox();
        row.setId(id);
        row.setRoutingKey("order.paid");
        row.setAttempts(1);
        return row;
    }

    @Test
    @DisplayName("锁未获取 → 直接返回，不扫描")
    void lockNotAcquiredSkips() {
        when(redissonClient.getLock(OutboxRelayScheduler.LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);
        scheduler.run();
        verify(outboxRepository, never()).listDue(any(), anyInt());
    }

    @Test
    @DisplayName("锁获取 → 扫描到期行逐条 deliver；一条抛错不阻塞其余；finally 解锁")
    void relayContinuesOnSingleFailure() {
        when(redissonClient.getLock(OutboxRelayScheduler.LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        EventOutbox a = row(1L);
        EventOutbox b = row(2L);
        EventOutbox c = row(3L);
        when(outboxRepository.listDue(any(), eq(OutboxRelayScheduler.BATCH_LIMIT))).thenReturn(List.of(a, b, c));
        when(eventsPublisher.deliver(a)).thenReturn(true);
        when(eventsPublisher.deliver(b)).thenThrow(new RuntimeException("boom"));
        when(eventsPublisher.deliver(c)).thenReturn(false);

        scheduler.run();

        verify(eventsPublisher).deliver(a);
        verify(eventsPublisher).deliver(b);
        verify(eventsPublisher).deliver(c);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("relayDue 返回成功投递数；空批返回 0 幂等")
    void relayDueCountsSent() {
        when(outboxRepository.listDue(any(), anyInt())).thenReturn(List.of(row(1L), row(2L)), List.of());
        when(eventsPublisher.deliver(any())).thenReturn(true, false);
        assertThat(scheduler.relayDue()).isEqualTo(1);
        assertThat(scheduler.relayDue()).isZero();
    }
}
