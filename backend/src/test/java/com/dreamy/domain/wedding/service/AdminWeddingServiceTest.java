package com.dreamy.domain.wedding.service;

import com.dreamy.domain.wedding.entity.RealWedding;
import com.dreamy.domain.wedding.repository.RealWeddingRepository;
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
class AdminWeddingServiceTest {

    @Mock
    RealWeddingRepository weddingRepository;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    CatalogQueryPort catalogQueryPort;
    @Mock
    com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;
    @InjectMocks
    AdminWeddingService service;

    @Test
    @DisplayName("TX-MKT-017: 婚礼案例删除先清商品关联/译文再物理删除主表")
    void deleteCleansChildrenBeforePhysicalDelete() {
        RealWedding wedding = new RealWedding();
        wedding.setId(1L);
        wedding.setCouple("Emma & James");
        wedding.setStatus(PublishStatus.PUBLISHED);
        when(weddingRepository.findById(1L)).thenReturn(wedding);

        service.delete(1L);

        InOrder order = inOrder(weddingRepository);
        order.verify(weddingRepository).deleteProductsByWeddingId(1L);
        order.verify(weddingRepository).deleteTranslationsByWeddingId(1L);
        order.verify(weddingRepository).deleteById(1L);
        verify(weddingRepository, never()).update(any());
        verify(audit).record("删除婚礼案例", "Emma & James", null);
        verify(cacheTasks).enqueue(anyString(), eq("wedding.delete"), eq("wedding"), eq(1L),
                eq("Emma & James"), anyList(), nullable(java.time.LocalDateTime.class), anyMap(),
                nullable(String.class));
    }
}
