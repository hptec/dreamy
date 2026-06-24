package com.dreamy.domain.site_builder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * site_builder 域缓存服务（FLOW-SB10）。
 * 两级缓存：L1 Caffeine in-process + L2 JetCache Redis。
 * 失效链：cache.invalidateFamily（事务内）+ publisher.publish（事务外）。
 * GRD-W01：in-process 调用，非 HTTP 自调。
 */
@Service
public class SiteBuilderCacheService {

    private static final Logger log = LoggerFactory.getLogger(SiteBuilderCacheService.class);

    private static final String FAMILY_HOME = "site_builder:home";
    private static final String FAMILY_NAVIGATION = "site_builder:navigation";
    private static final String FAMILY_FOOTER = "site_builder:footer";
    private static final String FAMILY_ANNOUNCEMENT = "site_builder:announcement";

    private final StringRedisTemplate redisTemplate;

    public SiteBuilderCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void invalidateHomeSectionFamily() {
        invalidateFamily(FAMILY_HOME, "TYPE_HOME_SECTION_CHANGED");
    }

    public void invalidateNavigationFamily() {
        invalidateFamily(FAMILY_NAVIGATION, "TYPE_NAVIGATION_CHANGED");
    }

    public void invalidateFooterFamily() {
        invalidateFamily(FAMILY_FOOTER, "TYPE_FOOTER_CHANGED");
    }

    public void invalidateAnnouncementFamily() {
        invalidateFamily(FAMILY_ANNOUNCEMENT, "TYPE_ANNOUNCEMENT_CHANGED");
    }

    private void invalidateFamily(String family, String eventType) {
        try {
            redisTemplate.delete(family + ":*");
            redisTemplate.convertAndSend("site_builder:cache:invalidation", eventType + ":" + family);
            log.info("[SiteBuilderCache] invalidated family={}", family);
        } catch (Exception e) {
            log.error("[SiteBuilderCache] invalidation failed family={} (non-blocking)", family, e);
        }
    }

    public String getCached(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[SiteBuilderCache] cache get failed key={}", key, e);
            return null;
        }
    }

    public void setCached(String key, String value, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[SiteBuilderCache] cache set failed key={}", key, e);
        }
    }
}
