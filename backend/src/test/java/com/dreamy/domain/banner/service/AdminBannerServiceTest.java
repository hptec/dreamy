package com.dreamy.domain.banner.service;

import com.dreamy.domain.banner.entity.Banner;
import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.enums.ContentStatus;
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
class AdminBannerServiceTest {

    @Mock
    BannerRepository bannerRepository;
    @Mock
    MarketingCacheService cache;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    MarketingAfterCommitRunner afterCommit;
    @Mock
    MarketingContentInvalidatedPublisher publisher;
    @InjectMocks
    AdminBannerService service;

    @Test
    @DisplayName("TX-MKT-009: Banner 删除先清译文再物理删除主表，并保留提交后失效")
    void deleteCleansTranslationsBeforePhysicalDelete() {
        Banner banner = new Banner();
        banner.setId(1L);
        banner.setName("Hero");
        banner.setStatus(ContentStatus.PUBLISHED);
        when(bannerRepository.findById(1L)).thenReturn(banner);

        service.delete(1L);

        InOrder order = inOrder(bannerRepository);
        order.verify(bannerRepository).deleteTranslationsByBannerId(1L);
        order.verify(bannerRepository).deleteById(1L);
        verify(bannerRepository, never()).update(any());
        verify(audit).record("删除Banner", "Hero", null);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).run(callback.capture());
        callback.getValue().run();
        verify(cache).invalidateFamily(Family.BANNERS);
        verify(publisher).publish(MarketingContentInvalidatedPublisher.TYPE_BANNER_CHANGED);
    }
}
