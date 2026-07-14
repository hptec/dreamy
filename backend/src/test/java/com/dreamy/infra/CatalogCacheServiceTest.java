package com.dreamy.infra;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.dreamy.infra.CatalogCacheService.Family;
import com.dreamy.infra.CatalogCacheService.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogCacheServiceTest {

    @Mock private CacheManager cacheManager;
    @Mock private Cache<String, Object> cache;
    @Mock private StringRedisTemplate redis;

    private CatalogCacheService service;

    @BeforeEach
    void setUp() {
        when(cacheManager.<String, Object>getOrCreateCache(any())).thenReturn(cache);
        service = new CatalogCacheService(cacheManager, redis);
        service.initCaches();
    }

    @Test
    void familyInvalidationMovesAllInstancesToNextGeneration() {
        when(redis.execute(any(), anyList(), any(), any())).thenReturn(2L, 3L, 3L);

        Lookup oldLookup = service.lookup(Family.PRODUCTS, "all:en");
        service.invalidateFamily(Family.PRODUCTS);
        service.put(oldLookup, "old-products");
        Lookup newLookup = service.lookup(Family.PRODUCTS, "all:en");

        verify(cache).put("v2:all:en", "old-products");
        verify(cache).get("v3:all:en");
        assertThat(newLookup.generation()).isEqualTo(3L);
    }

    @Test
    void redisRecoveryPublishesFreshGenerationAboveLocalFallback() {
        String generationKey = "catalog:cache-generation:{products}";
        when(redis.execute(any(), anyList(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"))
                .thenReturn(7L, 7L);

        service.invalidateFamily(Family.PRODUCTS);
        Lookup recovered = service.lookup(Family.PRODUCTS, "all:en");
        Lookup converged = service.lookup(Family.PRODUCTS, "all:en");

        assertThat(recovered.generation()).isEqualTo(7L);
        assertThat(converged.generation()).isEqualTo(7L);
        verify(redis).execute(
                any(),
                eq(java.util.List.of(generationKey, generationKey + ":high-water")),
                eq("1"),
                eq("1"));
        verify(redis).execute(
                any(),
                eq(java.util.List.of(generationKey, generationKey + ":high-water")),
                eq("7"),
                eq("0"));
        verify(cache, times(2)).get("v7:all:en");
    }

    @Test
    void fallbackCreatedDuringRecoveryRemainsPendingForNextReconciliation() throws Exception {
        String generationKey = "catalog:cache-generation:{products}";
        CountDownLatch recoveryStarted = new CountDownLatch(1);
        CountDownLatch releaseRecovery = new CountDownLatch(1);
        AtomicInteger invocation = new AtomicInteger();
        when(redis.execute(any(), anyList(), any(), any())).thenAnswer(call -> {
            return switch (invocation.incrementAndGet()) {
                case 1, 3 -> throw new RedisConnectionFailureException("redis unavailable");
                case 2 -> {
                    recoveryStarted.countDown();
                    if (!releaseRecovery.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("recovery test timed out");
                    }
                    yield 7L;
                }
                case 4, 5 -> 8L;
                default -> throw new IllegalStateException("unexpected Redis invocation");
            };
        });

        service.invalidateFamily(Family.PRODUCTS);
        CompletableFuture<Lookup> recovering = CompletableFuture.supplyAsync(
                () -> service.lookup(Family.PRODUCTS, "all:en"));
        assertThat(recoveryStarted.await(5, TimeUnit.SECONDS)).isTrue();

        service.invalidateFamily(Family.PRODUCTS);
        releaseRecovery.countDown();
        assertThat(recovering.get(5, TimeUnit.SECONDS).generation()).isEqualTo(7L);

        Lookup reconciled = service.lookup(Family.PRODUCTS, "all:en");
        Lookup converged = service.lookup(Family.PRODUCTS, "all:en");

        assertThat(reconciled.generation()).isEqualTo(8L);
        assertThat(converged.generation()).isEqualTo(8L);
        verify(redis).execute(
                any(),
                eq(java.util.List.of(generationKey, generationKey + ":high-water")),
                eq("7"),
                eq("1"));
        verify(redis).execute(
                any(),
                eq(java.util.List.of(generationKey, generationKey + ":high-water")),
                eq("8"),
                eq("0"));
    }
}
