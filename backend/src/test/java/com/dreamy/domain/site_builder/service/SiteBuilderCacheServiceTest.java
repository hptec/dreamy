package com.dreamy.domain.site_builder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteBuilderCacheServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    @Test
    void invalidationUsesSharedGenerationInsteadOfLiteralWildcardKey() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("site_builder:home:generation")).thenReturn(3L);
        SiteBuilderCacheService service = new SiteBuilderCacheService(redis);

        service.invalidateHomeSectionFamily();

        verify(values).increment("site_builder:home:generation");
        verify(redis).convertAndSend("site_builder:cache:invalidation",
                "TYPE_HOME_SECTION_CHANGED:site_builder:home:3");
    }
}
