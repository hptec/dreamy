package com.dreamy.domain.guide.service;

import com.dreamy.domain.guide.entity.Guide;
import com.dreamy.domain.guide.repository.GuideRepository;
import com.dreamy.enums.PublishStatus;
import com.dreamy.infra.MarketingAfterCommitRunner;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.mq.MarketingContentInvalidatedPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuideServiceTest {

    @Mock
    GuideRepository guideRepository;
    @Mock
    MarketingCacheService cache;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    MarketingAfterCommitRunner afterCommit;
    @Mock
    MarketingContentInvalidatedPublisher publisher;
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
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).run(callback.capture());
        callback.getValue().run();
        verify(cache).invalidateFamily(Family.GUIDES);
        verify(publisher).publish(MarketingContentInvalidatedPublisher.TYPE_GUIDE_CHANGED);
    }
}
