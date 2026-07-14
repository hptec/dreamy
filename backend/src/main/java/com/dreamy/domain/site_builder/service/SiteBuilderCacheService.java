package com.dreamy.domain.site_builder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


/**
 * site_builder 域缓存服务（FLOW-SB10）。
 * site_builder 失效信号。消费端聚合当前直接读 DB/下游服务，不在此组件缓存响应；
 * 版本号供后续缓存接入时作为共享 namespace，避免把通配符当普通 Redis key 删除。
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
            Long version = redisTemplate.opsForValue().increment(family + ":generation");
            redisTemplate.convertAndSend("site_builder:cache:invalidation",
                    eventType + ":" + family + ":" + version);
            log.info("[SiteBuilderCache] invalidated family={} generation={}", family, version);
        } catch (Exception e) {
            log.error("[SiteBuilderCache] invalidation failed family={} (non-blocking)", family, e);
        }
    }

}
