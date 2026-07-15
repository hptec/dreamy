package com.dreamy.domain.cache.service;

import com.dreamy.domain.cache.entity.CacheInvalidationStep;
import com.dreamy.domain.cache.entity.CacheInvalidationTask;
import com.dreamy.domain.cache.repository.CacheInvalidationStepRepository;
import com.dreamy.domain.cache.repository.CacheInvalidationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationTaskServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 3, 0);

    @Mock CacheInvalidationTaskRepository tasks;
    @Mock CacheInvalidationStepRepository steps;
    CacheInvalidationTaskService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T03:00:00Z"), ZoneOffset.UTC);
        service = new CacheInvalidationTaskService(tasks, steps, clock);
    }

    @Test
    void enqueuePersistsUtcScheduleAndExplicitTargets() {
        LocalDateTime executeAt = NOW.plusHours(2);

        service.enqueue(CacheInvalidationTaskService.MODE_SCHEDULED, "banner.window.start",
                "banner", 7L, "Hero", List.of(CacheInvalidationTarget.MARKETING_BANNERS),
                executeAt, Map.of("boundary", "start"), "scheduler");

        ArgumentCaptor<CacheInvalidationTask> captor = ArgumentCaptor.forClass(CacheInvalidationTask.class);
        verify(tasks).insert(captor.capture());
        CacheInvalidationTask task = captor.getValue();
        assertThat(task.getTriggeredAt()).isEqualTo(NOW);
        assertThat(task.getScheduledAt()).isEqualTo(executeAt);
        assertThat(task.getStatus()).isEqualTo(CacheInvalidationTask.STATUS_SCHEDULED);
        assertThat(task.getTargets()).containsExactly("MARKETING_BANNERS");
        assertThat(task.getCorrelationId()).isNotBlank();
    }

    @Test
    void failedAttemptIsRetriedWithExponentialBackoff() {
        CacheInvalidationTask task = task(CacheInvalidationTask.STATUS_RUNNING, 0, 3);
        when(tasks.selectById(9L)).thenReturn(task);

        service.finishAttempt(9L, 1, 1, "redis unavailable");

        assertThat(task.getStatus()).isEqualTo(CacheInvalidationTask.STATUS_RETRYING);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getNextRetryAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(task.getCompletedAt()).isNull();
        verify(tasks).updateById(task);
    }

    @Test
    void staleRunningTaskConsumesAttemptAndEventuallyFails() {
        CacheInvalidationTask task = task(CacheInvalidationTask.STATUS_RUNNING, 2, 3);
        task.setId(12L);
        when(tasks.findStaleRunning(NOW.minus(Duration.ofMinutes(2)), 100)).thenReturn(List.of(task));

        int recovered = service.recoverStaleRunning(Duration.ofMinutes(2));

        assertThat(recovered).isEqualTo(1);
        assertThat(task.getAttemptCount()).isEqualTo(3);
        assertThat(task.getStatus()).isEqualTo(CacheInvalidationTask.STATUS_FAILED);
        assertThat(task.getCompletedAt()).isEqualTo(NOW);
        verify(steps).failRunningByTaskId(eq(12L), eq(NOW), contains("worker interrupted"));
        verify(tasks).updateById(task);
    }

    private static CacheInvalidationTask task(int status, int attempts, int maxAttempts) {
        CacheInvalidationTask task = new CacheInvalidationTask();
        task.setStatus(status);
        task.setAttemptCount(attempts);
        task.setMaxAttempts(maxAttempts);
        return task;
    }
}
