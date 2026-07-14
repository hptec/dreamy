package com.dreamy.domain.lookbook.service;

import com.dreamy.domain.lookbook.entity.Lookbook;
import com.dreamy.domain.lookbook.repository.LookbookRepository;
import com.dreamy.enums.PublishStatus;
import com.dreamy.infra.MarketingAfterCommitRunner;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.mq.MarketingContentInvalidatedPublisher;
import com.dreamy.port.CatalogQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLookbookServiceTest {

    @Mock
    LookbookRepository lookbookRepository;
    @Mock
    MarketingCacheService cache;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    MarketingAfterCommitRunner afterCommit;
    @Mock
    MarketingContentInvalidatedPublisher publisher;
    @Mock
    CatalogQueryPort catalogQueryPort;
    @InjectMocks
    AdminLookbookService service;

    @Test
    @DisplayName("TX-MKT-021: Lookbook 删除先清商品关联/译文再物理删除主表")
    void deleteCleansChildrenBeforePhysicalDelete() {
        Lookbook lookbook = new Lookbook();
        lookbook.setId(1L);
        lookbook.setTitle("Vineyard");
        lookbook.setStatus(PublishStatus.PUBLISHED);
        when(lookbookRepository.findById(1L)).thenReturn(lookbook);

        service.delete(1L);

        InOrder order = inOrder(lookbookRepository);
        order.verify(lookbookRepository).deleteProductsByLookbookId(1L);
        order.verify(lookbookRepository).deleteTranslationsByLookbookId(1L);
        order.verify(lookbookRepository).deleteById(1L);
        verify(lookbookRepository, never()).update(any());
        verify(audit).record("删除Lookbook", "Vineyard", null);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).run(callback.capture());
        callback.getValue().run();
        verify(cache).invalidateFamily(Family.LOOKBOOKS);
        verify(cache).invalidateFamily(Family.LOOKBOOK);
        verify(publisher).publish(eq(MarketingContentInvalidatedPublisher.TYPE_LOOKBOOK_CHANGED),
                isNull(), isNull(), eq(1L));
    }
}
