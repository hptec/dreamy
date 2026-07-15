package com.dreamy.sched;

import com.dreamy.domain.cache.entity.CacheInvalidationTask;
import com.dreamy.domain.cache.service.CacheInvalidationDispatcher;
import com.dreamy.domain.cache.service.CacheInvalidationTarget;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationTaskWorkerTest {

    @Mock RedissonClient redisson;
    @Mock CacheInvalidationTaskService tasks;
    @Mock CacheInvalidationDispatcher dispatcher;

    @Test
    void executeRecordsEveryTargetAndPartialOutcome() {
        CacheInvalidationTask task = new CacheInvalidationTask();
        task.setId(5L);
        task.setAttemptCount(0);
        task.setTargets(List.of("CATALOG_PRODUCTS", "MARKETING_BANNERS"));
        when(tasks.claim(5L)).thenReturn(task);
        when(tasks.beginStep(5L, 1, "CATALOG_PRODUCTS")).thenReturn(51L);
        when(tasks.beginStep(5L, 1, "MARKETING_BANNERS")).thenReturn(52L);
        when(dispatcher.execute(CacheInvalidationTarget.CATALOG_PRODUCTS)).thenReturn("shared generation=8");
        when(dispatcher.execute(CacheInvalidationTarget.MARKETING_BANNERS))
                .thenThrow(new IllegalStateException("redis unavailable"));
        CacheInvalidationTaskWorker worker = new CacheInvalidationTaskWorker(redisson, tasks, dispatcher, 120);

        worker.execute(5L);

        verify(tasks).completeStep(51L, true, "shared generation=8", null);
        verify(tasks).completeStep(eq(52L), eq(false), isNull(), contains("redis unavailable"));
        verify(tasks).finishAttempt(eq(5L), eq(1), eq(1), contains("MARKETING_BANNERS"));
    }
}
