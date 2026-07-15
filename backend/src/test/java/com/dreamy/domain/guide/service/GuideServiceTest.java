package com.dreamy.domain.guide.service;

import com.dreamy.domain.guide.entity.Guide;
import com.dreamy.domain.guide.repository.GuideRepository;
import com.dreamy.enums.PublishStatus;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.infra.MarketingCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuideServiceTest {

    @Mock
    GuideRepository guideRepository;
    @Mock
    MarketingCacheService cache;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;
    @InjectMocks
    GuideService service;

    @Test
    @DisplayName("TX-MKT-025: 指南删除先清译文再物理删除主表")
    void deleteCleansTranslationsBeforePhysicalDelete() {
        Guide guide = new Guide();
        guide.setId(1L);
        guide.setTitle("Planning");
        guide.setStatus(PublishStatus.PUBLISHED);
        when(guideRepository.findById(1L)).thenReturn(guide);

        service.delete(1L);

        InOrder order = inOrder(guideRepository);
        order.verify(guideRepository).deleteTranslationsByGuideId(1L);
        order.verify(guideRepository).deleteById(1L);
        verify(guideRepository, never()).update(any());
        verify(audit).record("删除指南", "Planning", null);
        verify(cacheTasks).enqueue(anyString(), eq("guide.delete"), eq("guide"), eq(1L), eq("Planning"),
                anyList(), nullable(java.time.LocalDateTime.class), anyMap(), nullable(String.class));
    }
}
