package com.dreamy.domain.lookbook.service;

import com.dreamy.domain.lookbook.entity.Lookbook;
import com.dreamy.domain.lookbook.repository.LookbookRepository;
import com.dreamy.enums.PublishStatus;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.port.CatalogQueryPort;
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
class AdminLookbookServiceTest {

    @Mock
    LookbookRepository lookbookRepository;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    CatalogQueryPort catalogQueryPort;
    @Mock
    com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;
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
        verify(cacheTasks).enqueue(anyString(), eq("lookbook.delete"), eq("lookbook"), eq(1L), eq("Vineyard"),
                anyList(), nullable(java.time.LocalDateTime.class), anyMap(), nullable(String.class));
    }
}
