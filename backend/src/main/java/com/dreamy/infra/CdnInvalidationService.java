package com.dreamy.infra;

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

    /**
     * 异步清除 CDN 缓存。
     * @param paths 路径列表（相对路径，如 /product/white-dress）
     */
    @Async("cdnInvalidationExecutor")
    public void invalidatePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            log.debug("CDN invalidation: no paths to invalidate");
            return;
        }

        log.info("CDN invalidation: provider={}, paths={}", provider, paths);

        try {
            switch (provider) {
                case "cloudflare":
                    invalidateCloudflare(paths);
                    break;
                case "stub":
                    // stub 模式：仅记录日志，不实际调用 CDN API
                    log.info("CDN invalidation (stub mode): would purge {} paths: {}", paths.size(), paths);
                    break;
                default:
                    log.warn("Unknown CDN provider: {}", provider);
            }
        } catch (Exception e) {
            log.error("CDN invalidation failed: provider={}, paths={}, error={}", provider, paths, e.getMessage(), e);
        }
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
            log.info("Cloudflare purge success: urls=, response={}", urls, response.body());
        } else {
            log.error("Cloudflare purge failed: status={}, body={}", response.statusCode(), response.body());
            throw new RuntimeException("Cloudflare purge failed: " + response.statusCode());
        }
    }

    /**
     * 简单的 JSON 数组序列化（避免引入 Jackson 依赖）。
     */
    private String toJsonArray(List<String> items) {
        return "[" + String.join(",", items.stream().map(s -> "\"" + s + "\"").toList()) + "]";
    }
}
