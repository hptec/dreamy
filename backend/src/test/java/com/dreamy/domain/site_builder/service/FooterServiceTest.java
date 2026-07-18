package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.site_builder.repository.FooterRepository;
import com.dreamy.dto.SiteBuilderDtos.FooterColumnUpsert;
import com.dreamy.dto.SiteBuilderDtos.FooterLinkUpsert;
import com.dreamy.dto.SiteBuilderDtos.FooterSaveRequest;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void saveAcceptsRelativeAndAbsoluteLinkUrls() {
        FooterLinkUpsert relative = new FooterLinkUpsert();
        relative.setLabel("All Dresses");
        relative.setUrl("/products");
        FooterLinkUpsert absolute = new FooterLinkUpsert();
        absolute.setLabel("Instagram");
        absolute.setUrl("https://instagram.com/dreamy");
        FooterColumnUpsert column = new FooterColumnUpsert();
        column.setTitle("Shop");
        column.setLinks(List.of(relative, absolute));
        FooterSaveRequest request = new FooterSaveRequest();
        request.setColumns(List.of(column));
        when(repository.findAllColumnsOrderBySort()).thenReturn(List.of());
        when(repository.findAllLinksOrderBySort()).thenReturn(List.of());
        FooterService service = new FooterService(repository, new ObjectMapper(), cacheTasks);

        assertThat(service.save(request)).isEmpty();

        verify(repository, times(2)).insertLink(any());
    }

    @Test
    void saveRejectsNonHttpNonRelativeLinkUrls() {
        FooterLinkUpsert bad = new FooterLinkUpsert();
        bad.setLabel("XSS");
        bad.setUrl("javascript:alert(1)");
        FooterColumnUpsert column = new FooterColumnUpsert();
        column.setTitle("Shop");
        column.setLinks(List.of(bad));
        FooterSaveRequest request = new FooterSaveRequest();
        request.setColumns(List.of(column));
        FooterService service = new FooterService(repository, new ObjectMapper(), cacheTasks);

        assertThatThrownBy(() -> service.save(request)).isInstanceOf(SiteBuilderException.class);
    }
}
