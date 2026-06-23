package com.dreamy.infra;

import com.dreamy.domain.cache.entity.CacheInvalidationLog;
import com.dreamy.domain.cache.service.AdminCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * CDN 缓存清除服务。
 * 调用 CDN 提供商的 API 清除指定路径的缓存。
 *
 * 支持的 CDN：
 * - Cloudflare（通过 Zone Purge API）
 * - 可扩展其他 CDN 提供商
 */
@Service
public class CdnInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(CdnInvalidationService.class);

    @Value("${cdn.provider:stub}")
    private String provider;

    @Value("${cdn.cloudflare.zone-id:}")
    private String cloudflareZoneId;

    @Value("${cdn.cloudflare.api-token:}")
    private String cloudflareApiToken;

    @Value("${cdn.base-url:https://dreamy.com}")
    private String cdnBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final AdminCacheService cacheService;

    public CdnInvalidationService(AdminCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 异步清除 CDN 缓存（带日志回写）。
     * @param paths 路径列表（相对路径，如 /product/white-dress）
     * @param logId 对应 cache_invalidation_log 记录 ID，完成后回写状态；null 不回写
     */
    @Async("cdnInvalidationExecutor")
    public void invalidatePaths(List<String> paths, Long logId) {
        if (paths == null || paths.isEmpty()) {
            log.debug("CDN invalidation: no paths to invalidate");
            if (logId != null) {
                cacheService.updateLogStatus(logId, CacheInvalidationLog.STATUS_COMPLETED, null);
            }
            return;
        }

        log.info("CDN invalidation: provider={}, paths={}, logId={}", provider, paths, logId);

        try {
            switch (provider) {
                case "cloudflare":
                    invalidateCloudflare(paths);
                    if (logId != null) {
                        cacheService.updateLogStatus(logId, CacheInvalidationLog.STATUS_COMPLETED, null);
                    }
                    break;
                case "stub":
                    // stub 模式：仅记录日志，不实际调用 CDN API；标记为完成
                    log.info("CDN invalidation (stub mode): would purge {} paths: {}", paths.size(), paths);
                    if (logId != null) {
                        cacheService.updateLogStatus(logId, CacheInvalidationLog.STATUS_COMPLETED, null);
                    }
                    break;
                default:
                    log.warn("Unknown CDN provider: {}", provider);
                    if (logId != null) {
                        cacheService.updateLogStatus(logId, CacheInvalidationLog.STATUS_FAILED, "Unknown CDN provider: " + provider);
                    }
            }
        } catch (Exception e) {
            log.error("CDN invalidation failed: provider={}, paths={}, logId={}, error={}", provider, paths, logId, e.getMessage(), e);
            if (logId != null) {
                cacheService.updateLogStatus(logId, CacheInvalidationLog.STATUS_FAILED, e.getMessage());
            }
        }
    }

    /** 兼容旧调用：不带 logId，不回写状态 */
    public void invalidatePaths(List<String> paths) {
        invalidatePaths(paths, null);
    }

    /**
     * Cloudflare Zone Purge API。
     * https://developers.cloudflare.com/api/operations/zone-purge
     */
    private void invalidateCloudflare(List<String> paths) throws Exception {
        if (cloudflareZoneId == null || cloudflareZoneId.isBlank()) {
            log.warn("Cloudflare zone ID not configured, skipping CDN invalidation");
            return;
        }

        // 将相对路径转为完整 URL
        List<String> urls = paths.stream()
                .map(p -> cdnBaseUrl + (p.startsWith("/") ? p : "/" + p))
                .toList();

        String apiUrl = String.format("https://api.cloudflare.com/client/v4/zones/%s/purge_cache", cloudflareZoneId);
        String requestBody = String.format("{\"files\":%s}", toJsonArray(urls));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + cloudflareApiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Cloudflare purge success: urls={}, response={}", urls, response.body());
        } else {
            log.error("Cloudflare purge failed: status={}, body={}", response.statusCode(), response.body());
            throw new RuntimeException("Cloudflare purge failed: status=" + response.statusCode() + ", body=" + response.body());
        }
    }

    /**
     * 简单的 JSON 数组序列化（避免引入 Jackson 依赖）。
     */
    private String toJsonArray(List<String> items) {
        return "[" + String.join(",", items.stream().map(s -> "\"" + s + "\"").toList()) + "]";
    }
}
