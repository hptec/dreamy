package com.dreamy.marketing.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cloudflare purge 客户端（EVT-MKT-002 ④：zone token 后端配置，按完整 URL 列表 purge——stub/real 双模式）。
 * real 模式失败抛出 → 消费者 nack 重试 ×3 → dreamy.dlq；期间 CDN 靠 s-maxage TTL + serve-stale 兜底（决策 22）。
 * 密钥 api-token 不落日志（error-strategy 密钥面）。
 */
@Component
public class CloudflarePurgeClient {

    private static final Logger log = LoggerFactory.getLogger(CloudflarePurgeClient.class);

    private final InvalidateProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CloudflarePurgeClient(InvalidateProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeoutMs()))
                .build();
    }

    /** paths → site-base-url 拼全 URL → purge；失败抛 IllegalStateException → nack */
    public void purge(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        List<String> urls = paths.stream().map(p -> props.getSiteBaseUrl() + p).toList();
        if (!props.isReal()) {
            log.info("[CF-PURGE-STUB] urls={}", urls);
            return;
        }
        if (props.getCloudflare().getZoneId().isBlank() || props.getCloudflare().getApiToken().isBlank()) {
            // real 模式缺凭证：记告警跳过（不可重试错误，nack 无意义——配置缺失人工介入）
            log.warn("[CF-PURGE] zone-id/api-token missing, purge skipped ({} urls, TTL 兜底)", urls.size());
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of("files", urls));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getCloudflare().getApiBaseUrl() + "/zones/"
                            + props.getCloudflare().getZoneId() + "/purge_cache"))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getCloudflare().getApiToken())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("cloudflare purge returned " + response.statusCode());
            }
            log.info("[CF-PURGE] {} urls purged", urls.size());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("cloudflare purge interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("cloudflare purge failed", ex);
        }
    }
}
