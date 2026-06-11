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
 * Next.js on-demand revalidate 客户端（EVT-MKT-002 ③：`POST {NEXT_INTERNAL_URL}/api/revalidate {paths[]}`，
 * header `x-revalidate-token` 共享密钥，仅内网——FLOW-P03）。
 * stub 模式仅日志（dev 缺省，与 mq/storage stub 惯例一致）；real 模式非 2xx/网络异常抛出 → 消费者 nack 重试。
 */
@Component
public class NextRevalidateClient {

    private static final Logger log = LoggerFactory.getLogger(NextRevalidateClient.class);

    private final InvalidateProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NextRevalidateClient(InvalidateProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeoutMs()))
                .build();
    }

    /** 失败抛 IllegalStateException → 消费者 nack（EVT-MKT-002 ⑤） */
    public void revalidate(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        if (!props.isReal()) {
            log.info("[REVALIDATE-STUB] paths={}", paths);
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of("paths", paths));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getNextInternalUrl() + "/api/revalidate"))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("x-revalidate-token", props.getRevalidateToken())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("revalidate endpoint returned " + response.statusCode());
            }
            log.info("[REVALIDATE] {} paths revalidated", paths.size());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("revalidate interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("revalidate call failed", ex);
        }
    }
}
