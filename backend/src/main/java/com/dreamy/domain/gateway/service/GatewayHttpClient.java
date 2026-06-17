package com.dreamy.domain.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部网关（OpenAI 兼容协议）HTTP 调用客户端（决策 6.3 韧性模式 / EXT 外部集成）。
 * L2 TRACE: i18n-backend-api-detail.md §1.6/§1.7/§2.1 / §6.3 / EDGE-015/016/017/023。
 *
 * 两类调用：
 * - listModels：GET {base_url}/v1/models，超时 10s（test/sync-models 共用）；
 * - chatCompletion：POST {base_url}/v1/chat/completions，超时 30s（翻译）。
 *
 * 异常语义由调用方按 GatewayCallException.kind 映射错误码（不可达/鉴权/超时/客户端/服务端）。
 * 日志脱敏：apiKey 绝不入日志（logging_rules.api_key_masking）。
 */
@Component
public class GatewayHttpClient {

    private static final Logger log = LoggerFactory.getLogger(GatewayHttpClient.class);

    static final Duration MODELS_TIMEOUT = Duration.ofSeconds(10);
    static final Duration CHAT_TIMEOUT = Duration.ofSeconds(30);
    private static final int CHAT_MAX_TOKENS = 2000;

    private final ObjectMapper objectMapper;

    public GatewayHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 调用结果失败分类，供调用方映射到具体错误码。 */
    public enum FailureKind {
        UNREACHABLE,   // DNS/连接拒绝 → 502201
        AUTH_FAILED,   // 401/403 → 502202
        TIMEOUT,       // 超时 → 504201/504301
        CLIENT_ERROR,  // 其它 4xx → 502301
        SERVER_ERROR,  // 5xx → 502301
        RATE_LIMITED   // 429 → 限流
    }

    /** 网关调用异常，携带失败分类与可选 HTTP 状态。 */
    public static class GatewayCallException extends RuntimeException {
        private final transient FailureKind kind;
        private final Integer httpStatus;

        public GatewayCallException(FailureKind kind, Integer httpStatus, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
            this.httpStatus = httpStatus;
        }

        public FailureKind getKind() {
            return kind;
        }

        public Integer getHttpStatus() {
            return httpStatus;
        }
    }

    /**
     * GET /v1/models → 解析 data[].id 列表。超时 10s。失败抛 GatewayCallException。
     */
    public List<String> listModels(String baseUrl, String apiKey) {
        String body = exec("GET", joinUrl(baseUrl, "/v1/models"), apiKey, null, MODELS_TIMEOUT);
        return parseModelIds(body);
    }

    /**
     * POST /v1/chat/completions → 返回原始响应体 JSON 字符串。超时 30s。失败抛 GatewayCallException。
     */
    public String chatCompletion(String baseUrl, String apiKey, String model,
                                 String systemPrompt, String userText) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userText)),
                "max_tokens", CHAT_MAX_TOKENS);
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new GatewayCallException(FailureKind.CLIENT_ERROR, null, "payload 序列化失败", ex);
        }
        return exec("POST", joinUrl(baseUrl, "/v1/chat/completions"), apiKey, jsonPayload, CHAT_TIMEOUT);
    }

    /** 解析 /v1/models 响应 data[].id。 */
    public List<String> parseModelIds(String body) {
        List<String> ids = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return ids;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode node : data) {
                    String id = node.path("id").asText(null);
                    if (id != null && !id.isBlank()) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[GATEWAY-HTTP] /v1/models 响应解析失败", ex);
        }
        return ids;
    }

    /** 解析 /v1/chat/completions 译文（choices[0].message.content）。 */
    public String parseChatContent(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText(null);
                return content == null || content.isBlank() ? null : content.trim();
            }
        } catch (Exception ex) {
            log.warn("[GATEWAY-HTTP] chat 响应解析失败", ex);
        }
        return null;
    }

    /** 解析 token usage JSON（透传 usage 对象字符串，落库 token_usage 列）。 */
    public String parseTokenUsage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode usage = objectMapper.readTree(body).path("usage");
            return usage.isMissingNode() || usage.isNull() ? null : objectMapper.writeValueAsString(usage);
        } catch (Exception ex) {
            return null;
        }
    }

    /** 执行 HTTP 调用，统一异常分类。 */
    private String exec(String method, String url, String apiKey, String jsonBody, Duration timeout) {
        RestClient client = buildClient(timeout);
        try {
            RestClient.RequestBodySpec spec = client
                    .method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey);
            RestClient.RequestHeadersSpec<?> headersSpec = spec;
            if (jsonBody != null) {
                headersSpec = spec.header("Content-Type", "application/json").body(jsonBody);
            }
            org.springframework.http.ResponseEntity<String> entity = headersSpec
                    .retrieve()
                    .onStatus(s -> true, (rq, rs) -> { })
                    .toEntity(String.class);
            int status = entity.getStatusCode().value();
            String body = entity.getBody();
            if (status >= 200 && status < 300) {
                return body;
            }
            mapHttpError(status, body);
            return body; // unreachable
        } catch (GatewayCallException ex) {
            throw ex;
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            // 连接超时/读超时/连接拒绝/DNS
            Throwable cause = ex.getCause();
            if (cause instanceof java.net.SocketTimeoutException) {
                throw new GatewayCallException(FailureKind.TIMEOUT, null, "网关调用超时", ex);
            }
            throw new GatewayCallException(FailureKind.UNREACHABLE, null, "网关不可达", ex);
        } catch (Exception ex) {
            throw new GatewayCallException(FailureKind.UNREACHABLE, null, "网关调用异常", ex);
        }
    }

    private void mapHttpError(int status, String body) {
        if (status == 401 || status == 403) {
            throw new GatewayCallException(FailureKind.AUTH_FAILED, status, "网关鉴权失败(" + status + ")", null);
        }
        if (status == 429) {
            throw new GatewayCallException(FailureKind.RATE_LIMITED, status, "网关限流(429)", null);
        }
        if (status >= 500) {
            throw new GatewayCallException(FailureKind.SERVER_ERROR, status, "网关服务端错误(" + status + ")", null);
        }
        throw new GatewayCallException(FailureKind.CLIENT_ERROR, status, "网关客户端错误(" + status + ")", null);
    }

    private RestClient buildClient(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return RestClient.builder().requestFactory((ClientHttpRequestFactory) factory).build();
    }

    private String joinUrl(String baseUrl, String path) {
        if (baseUrl == null) {
            return path;
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // base_url 可能已含 /v1，避免重复
        if (trimmed.endsWith("/v1") && path.startsWith("/v1")) {
            return trimmed + path.substring(3);
        }
        return trimmed + path;
    }
}
