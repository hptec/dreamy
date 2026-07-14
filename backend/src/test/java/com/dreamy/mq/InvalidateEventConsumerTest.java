package com.dreamy.mq;

import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvalidateEventConsumerTest {

    @Mock
    EventIdempotencyGuard idempotencyGuard;
    @Mock
    NextRevalidateClient revalidateClient;
    @Mock
    CloudflarePurgeClient purgeClient;
    @Mock
    MarketingCacheService marketingCache;

    InvalidateEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InvalidateEventConsumer(idempotencyGuard, revalidateClient, purgeClient, marketingCache);
        when(idempotencyGuard.tryAcquire(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("product_* 事件失效所有嵌入 ProductRef 的 Marketing 缓存族")
    void productEventInvalidatesMarketingProductSnapshots() {
        DomainEvent event = new DomainEvent("evt-product", "content.invalidated", "2026-07-14T00:00:00Z",
                Map.of("type", "product_updated", "slug", "aurelia"));

        consumer.onEvent(event);

        verify(marketingCache).invalidateFamily(Family.WEDDINGS);
        verify(marketingCache).invalidateFamily(Family.WEDDING);
        verify(marketingCache).invalidateFamily(Family.LOOKBOOKS);
        verify(marketingCache).invalidateFamily(Family.LOOKBOOK);
        verify(marketingCache).invalidateFamily(Family.FLASH);
        verify(revalidateClient).revalidate(any());
        verify(purgeClient).purge(any());
    }

    @Test
    @DisplayName("非商品事件不切换 ProductRef 快照缓存代际")
    void nonProductEventDoesNotInvalidateProductSnapshots() {
        DomainEvent event = new DomainEvent("evt-banner", "content.invalidated", "2026-07-14T00:00:00Z",
                Map.of("type", "banner_changed"));

        consumer.onEvent(event);

        verify(marketingCache, never()).invalidateFamily(any());
        verify(revalidateClient).revalidate(any());
        verify(purgeClient).purge(any());
    }
}
