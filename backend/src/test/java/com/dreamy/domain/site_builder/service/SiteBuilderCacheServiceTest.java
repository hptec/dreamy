package com.dreamy.domain.site_builder.service;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteBuilderCacheServiceTest {

    @Mock CacheManager cacheManager;
    @Mock Cache<String, Object> cache;
    @Mock StringRedisTemplate redis;
    SiteBuilderCacheService service;

    @BeforeEach
    void setUp() {
        when(cacheManager.<String, Object>getOrCreateCache(any())).thenReturn(cache);
        service = new SiteBuilderCacheService(cacheManager, redis);
        service.initCaches();
    }

    @Test
    void homeUsesSharedGenerationAndVersionedLocaleKey() {
        when(redis.execute(any(), anyList(), any(), any())).thenReturn(8L, 9L);
        when(cache.get("v8:fr")).thenReturn("cached-home");

        SiteBuilderCacheService.Lookup lookup = service.lookup(SiteBuilderCacheService.Family.HOME, "fr");
        long invalidated = service.invalidateFamilyStrict(SiteBuilderCacheService.Family.HOME);

        assertThat(lookup.value()).isEqualTo("cached-home");
        assertThat(invalidated).isEqualTo(9L);
        verify(redis, times(2)).execute(any(),
                eq(java.util.List.of("site-builder:cache-generation:{home}",
                        "site-builder:cache-generation:{home}:high-water")), any(), any());
    }
}
