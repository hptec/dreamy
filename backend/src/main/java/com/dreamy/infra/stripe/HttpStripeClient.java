package com.dreamy.infra.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Stripe real 实现（dreamy.stripe.mode=real，沙箱/生产）。
 * JDK HttpClient 直连 Stripe REST（form-encoded），不引入 SDK 依赖；超时 10s（trading §0 StripePort）。
 * 失败 → 502601 StripeUnavailableException；超时 → 504601 StripeTimeoutException（BE-DIM-5 防腐层）。
 * 脱敏（error-strategy）：secret key 完全不落日志；错误日志仅记 HTTP 状态与资源路径，不回显响应体。
 */
@Component
@ConditionalOnProperty(name = "dreamy.stripe.mode", havingValue = "real")
public class HttpStripeClient implements StripeClient {

    private static final Logger log = LoggerFactory.getLogger(HttpStripeClient.class);

    private final StripeProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpStripeClient(StripeProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeoutMs()))
                .build();
    }

    @Override
    public StripePaymentIntent createPaymentIntent(long amountMinor, String currency, String orderNo,
                                                   Map<String, String> metadata) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("amount", String.valueOf(amountMinor));
        form.put("currency", currency);
        form.put("automatic_payment_methods[enabled]", "true");
        if (orderNo != null) {
            form.put("metadata[order_no]", orderNo);
        }
        if (metadata != null) {
            metadata.forEach((k, v) -> form.put("metadata[" + k + "]", v));
        }
        return toIntent(post("/v1/payment_intents", form));
    }

    @Override
    public StripePaymentIntent retrievePaymentIntent(String paymentIntentId) {
        return toIntent(get("/v1/payment_intents/" + paymentIntentId));
    }

    @Override
    public StripePaymentIntent cancelPaymentIntent(String paymentIntentId) {
        return toIntent(post("/v1/payment_intents/" + paymentIntentId + "/cancel", Map.of()));
    }

    @Override
    public StripeRefund createRefund(String paymentIntentId, Long amountMinor, String reason) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("payment_intent", paymentIntentId);
        if (amountMinor != null) {
            form.put("amount", String.valueOf(amountMinor));
        }
        if (reason != null) {
            form.put("reason", reason);
        }
        JsonNode node = post("/v1/refunds", form);
        return new StripeRefund(node.path("id").asText(), node.path("status").asText(),
                node.path("amount").asLong(), node.path("currency").asText());
    }

    private StripePaymentIntent toIntent(JsonNode node) {
        return new StripePaymentIntent(
                node.path("id").asText(),
                node.path("client_secret").isMissingNode() ? null : node.path("client_secret").asText(null),
                node.path("status").asText(),
                node.path("amount").asLong(),
                node.path("currency").asText());
    }

    private JsonNode post(String path, Map<String, String> form) {
        HttpRequest request = baseRequest(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
                .build();
        return execute(request, path);
    }

    private JsonNode get(String path) {
        HttpRequest request = baseRequest(path).GET().build();
        return execute(request, path);
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(props.getApiBase() + path))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Authorization", "Bearer " + props.getSecretKey());
    }

    private JsonNode execute(HttpRequest request, String path) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                // 不回显响应体（可能含用户/卡信息），仅记状态与路径
                log.error("[STRIPE] call failed path={} status={}", path, response.statusCode());
                throw new StripeUnavailableException("stripe responded " + response.statusCode(), null);
            }
            return mapper.readTree(response.body());
        } catch (HttpTimeoutException ex) {
            log.error("[STRIPE] call timeout path={} timeout_ms={}", path, props.getTimeoutMs());
            throw new StripeTimeoutException("stripe call timed out", ex);
        } catch (IOException ex) {
            log.error("[STRIPE] call unreachable path={} cause={}", path, ex.getClass().getSimpleName());
            throw new StripeUnavailableException("stripe unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new StripeUnavailableException("stripe call interrupted", ex);
        }
    }

    private String encodeForm(Map<String, String> form) {
        StringJoiner joiner = new StringJoiner("&");
        form.forEach((k, v) -> joiner.add(
                URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return joiner.toString();
    }
}
