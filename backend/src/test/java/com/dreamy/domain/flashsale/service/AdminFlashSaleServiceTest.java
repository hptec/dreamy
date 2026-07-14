package com.dreamy.domain.flashsale.service;

import com.dreamy.domain.flashsale.entity.FlashSale;
import com.dreamy.domain.flashsale.repository.FlashSaleRepository;
import com.dreamy.enums.FlashSaleStatus;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAfterCommitRunner;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.mq.MarketingContentInvalidatedPublisher;
import com.dreamy.port.CatalogQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFlashSaleServiceTest {

    @Mock
    FlashSaleRepository flashSaleRepository;
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
    AdminFlashSaleService service;

    @Test
    @DisplayName("TX-MKT-006: 仅 draft 可删；先清商品关联/译文再物理删除闪购主表")
    void deleteGuardsAndPhysicallyDeletes() {
        FlashSale scheduled = sale(1L, FlashSaleStatus.SCHEDULED);
        when(flashSaleRepository.findById(1L)).thenReturn(scheduled);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(MarketingException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((MarketingException) ex).getErrorCode())
                        .isEqualTo(MarketingErrorCode.CONTENT_STATE_INVALID));
        verify(flashSaleRepository, never()).deleteProductsByFlashId(1L);
        verify(flashSaleRepository, never()).deleteTranslationsByFlashId(1L);
        verify(flashSaleRepository, never()).deleteById(1L);

        FlashSale draft = sale(2L, FlashSaleStatus.DRAFT);
        when(flashSaleRepository.findById(2L)).thenReturn(draft);
        service.delete(2L);
        InOrder order = inOrder(flashSaleRepository);
        order.verify(flashSaleRepository).deleteProductsByFlashId(2L);
        order.verify(flashSaleRepository).deleteTranslationsByFlashId(2L);
        order.verify(flashSaleRepository).deleteById(2L);
        verify(flashSaleRepository, never()).update(any());
        verify(audit).record("删除闪购", "Flash sale", null);
        verify(afterCommit, never()).run(any(Runnable.class));
    }

    private static FlashSale sale(long id, FlashSaleStatus status) {
        FlashSale sale = new FlashSale();
        sale.setId(id);
        sale.setName("Flash sale");
        sale.setStatus(status);
        return sale;
    }
}
