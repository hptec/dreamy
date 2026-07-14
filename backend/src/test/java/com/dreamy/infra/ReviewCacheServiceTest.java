package com.dreamy.infra;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.dreamy.infra.ReviewCacheService.Family;
import com.dreamy.infra.ReviewCacheService.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewCacheServiceTest {

    @Mock private CacheManager cacheManager;
    @Mock private Cache<String, Object> cache;
    @Mock private StringRedisTemplate redis;

    private ReviewCacheService service;

    @BeforeEach
    void setUp() {
        when(cacheManager.<String, Object>getOrCreateCache(any())).thenReturn(cache);
        service = new ReviewCacheService(cacheManager, redis);
        service.initCaches();
    }

    @Test
    void productWriteInvalidatesSharedReviewGeneration() {
        when(redis.execute(any(), anyList(), any(), any())).thenReturn(0L, 1L, 1L);

        Lookup oldLookup = service.lookup(Family.REVIEWS, "42:newest:1:20");
        service.invalidateProduct(Family.REVIEWS, 42L);
        service.put(oldLookup, "old-review-page");
        Lookup newLookup = service.lookup(Family.REVIEWS, "42:newest:1:20");

        verify(cache).put("v0:42:newest:1:20", "old-review-page");
        verify(cache).get("v1:42:newest:1:20");
        assertThat(newLookup.generation()).isEqualTo(1L);
    }

    @Test
    void recoveredInvalidationAtomicallyAdvancesBeyondFallbackGeneration() {
        String generationKey = "review:cache-generation:{reviews}";
        when(redis.execute(any(), anyList(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"))
                .thenReturn(4L, 4L);

        service.invalidateProduct(Family.REVIEWS, 42L);
        service.invalidateProduct(Family.REVIEWS, 42L);
        Lookup lookup = service.lookup(Family.REVIEWS, "42:newest:1:20");

        assertThat(lookup.generation()).isEqualTo(4L);
        verify(redis).execute(
                any(),
                eq(java.util.List.of(generationKey, generationKey + ":high-water")),
                eq("1"),
                eq("1"));
        verify(redis).execute(
                any(),
                eq(java.util.List.of(generationKey, generationKey + ":high-water")),
                eq("4"),
                eq("0"));
        verify(cache).get("v4:42:newest:1:20");
    }
}
