package com.dreamy.identity.config;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.context.annotation.Configuration;

/**
 * JetCache 启用配置（分级缓存）。
 * 约束: BE-DIM-8 JetCache 分级（remote-only 会话/凭证 TTL30s 强一致 / two-level 资料配置 Caffeine+Redis）；
 * write_invalidate_rule 写即失效，由 @CacheInvalidate 在 Service 层落地。
 * 区域 area/缓存类型在 application.yml jetcache 节配置。
 */
@Configuration
@EnableMethodCache(basePackages = "com.dreamy.identity")
public class JetCacheConfig {
}
