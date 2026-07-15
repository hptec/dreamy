package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.site_builder.repository.FooterRepository;
import com.dreamy.dto.SiteBuilderDtos.FooterSaveRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FooterServiceTest {

    @Mock FooterRepository repository;
    @Mock CacheInvalidationTaskService cacheTasks;

    @Test
    void saveCreatesRealFooterCacheTask() {
        FooterSaveRequest request = new FooterSaveRequest();
        request.setColumns(List.of());
        when(repository.findAllColumnsOrderBySort()).thenReturn(List.of());
        when(repository.findAllLinksOrderBySort()).thenReturn(List.of());
        FooterService service = new FooterService(repository, new ObjectMapper(), cacheTasks);

        assertThat(service.save(request)).isEmpty();

        verify(repository).deleteAllLinks();
        verify(repository).deleteAllColumns();
        verify(cacheTasks).enqueue(anyString(), eq("site_footer.save"), eq("site_footer"),
                eq("footer"), eq("页脚配置"), anyList(), isNull(), anyMap(), isNull());
    }
}
