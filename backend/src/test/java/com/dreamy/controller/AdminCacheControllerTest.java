package com.dreamy.controller;

import com.dreamy.domain.cache.service.AdminCacheTaskQueryService;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCacheControllerTest {

    @Mock AdminCacheTaskQueryService queries;
    @Mock CacheInvalidationTaskService tasks;

    @Test
    void manualTaskRejectsUnknownTargetWithoutEnqueueing() {
        AdminCacheController controller = new AdminCacheController(queries, tasks);

        var response = controller.createManualTask(
                new AdminCacheController.ManualTaskRequest(List.of("SESSION_KEYS"), "test"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(tasks);
    }

    @Test
    void retryReturnsConflictForCompletedTask() {
        AdminCacheController controller = new AdminCacheController(queries, tasks);
        doThrow(new IllegalStateException("not retryable")).when(tasks).retry(9L);

        var response = controller.retry(9L);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void manualTaskTrimsReasonAndUsesRegisteredTarget() {
        AdminCacheController controller = new AdminCacheController(queries, tasks);
        when(tasks.enqueue(anyString(), anyString(), anyString(), isNull(), anyString(), anyList(),
                isNull(), anyMap(), isNull())).thenReturn(42L);

        var response = controller.createManualTask(new AdminCacheController.ManualTaskRequest(
                List.of("MARKETING_BANNERS"), "  emergency refresh  "));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(tasks).enqueue(eq(CacheInvalidationTaskService.MODE_MANUAL), eq("manual.invalidate"), eq("manual"),
                isNull(), eq("emergency refresh"), anyList(), isNull(),
                eq(java.util.Map.of("reason", "emergency refresh")), isNull());
    }
}
