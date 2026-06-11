package com.dreamy.analytics.infra.ga4;

import com.dreamy.analytics.domain.dashboard.service.RangeWindow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GA4 Data API real 客户端（SVC-ANA §5；决策 19；dreamy.ga4.mode=real）。
 * - 单次 batchRunReports（POST /v1beta/properties/{id}:batchRunReports）拉取三报表，一回合往返，不重试。
 * - service account 凭证：OAuth2 JWT Bearer 流（RS256 自签 assertion → token 端点换 access_token，
 *   进程内缓存至过期前 60s）；凭证内容/路径不入日志、不出任何响应（§5.4 脱敏）。
 * - 超时预算：connect 2s + read 8s（Ga4Properties）；HttpTimeoutException → timeout 形态（504001 兜底码），
 *   其余（HTTP 非 2xx/网络/解析）→ unavailable 形态（502001 兜底码）。
 * - 失败日志仅记 range、失败分类、耗时、HTTP 状态码（不含响应原文）。
 */
@Component
@ConditionalOnProperty(name = "dreamy.ga4.mode", havingValue = "real")
public class Ga4Client implements Ga4TrafficPort {

    private static final Logger log = LoggerFactory.getLogger(Ga4Client.class);

    private static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/analytics.readonly";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final String DATA_API_BASE = "https://analyticsdata.googleapis.com/v1beta/properties/";

    private final Ga4Properties properties;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public Ga4Client(Ga4Properties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** real 必填项启动校验失败快速暴露（§5.2） */
    @PostConstruct
    void validateAndInit() {
        if (properties.getPropertyId() == null || properties.getPropertyId().isBlank()) {
            throw new IllegalStateException("dreamy.ga4.property-id is required when dreamy.ga4.mode=real");
        }
        if (properties.getCredentialsPath() == null || properties.getCredentialsPath().isBlank()) {
            throw new IllegalStateException("dreamy.ga4.credentials-path is required when dreamy.ga4.mode=real");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    @Override
    public Ga4TrafficRaw fetch(RangeWindow range) {
        long startedAt = System.currentTimeMillis();
        try {
            String token = accessToken();
            String body = buildBatchRequestBody(range);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DATA_API_BASE + properties.getPropertyId() + ":batchRunReports"))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[GA4] fetch failed range={} class=unavailable status={} elapsedMs={}",
                        range.range(), response.statusCode(), System.currentTimeMillis() - startedAt);
                throw new Ga4FetchException("GA4 batchRunReports HTTP " + response.statusCode(), false);
            }
            return parseBatchResponse(response.body());
        } catch (Ga4FetchException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            log.warn("[GA4] fetch failed range={} class=timeout elapsedMs={}",
                    range.range(), System.currentTimeMillis() - startedAt);
            throw new Ga4FetchException("GA4 timeout", true, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Ga4FetchException("GA4 interrupted", true, ex);
        } catch (Exception ex) {
            // 不记异常 message（可能含响应原文/路径），仅记分类（§5.4 脱敏）
            log.warn("[GA4] fetch failed range={} class=unavailable type={} elapsedMs={}",
                    range.range(), ex.getClass().getSimpleName(), System.currentTimeMillis() - startedAt);
            throw new Ga4FetchException("GA4 unavailable", false, ex);
        }
    }

    // ===== OAuth2 service account JWT Bearer =====

    private String accessToken() throws Exception {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
                return cachedToken;
            }
            JsonNode credentials = objectMapper.readTree(
                    Files.readString(Path.of(properties.getCredentialsPath()), StandardCharsets.UTF_8));
            String clientEmail = credentials.path("client_email").asText();
            String tokenUri = credentials.path("token_uri").asText("https://oauth2.googleapis.com/token");
            RSAPrivateKey privateKey = parsePrivateKey(credentials.path("private_key").asText());

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(clientEmail)
                    .audience(tokenUri)
                    .claim("scope", OAUTH_SCOPE)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey));

            String form = "grant_type=" + URLEncoder.encode(GRANT_TYPE, StandardCharsets.UTF_8)
                    + "&assertion=" + URLEncoder.encode(jwt.serialize(), StandardCharsets.UTF_8);
            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUri))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() != 200) {
                throw new Ga4FetchException("GA4 token HTTP " + tokenResponse.statusCode(), false);
            }
            JsonNode tokenNode = objectMapper.readTree(tokenResponse.body());
            cachedToken = tokenNode.path("access_token").asText();
            tokenExpiresAt = Instant.now().plusSeconds(tokenNode.path("expires_in").asLong(3600));
            return cachedToken;
        }
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    // ===== batchRunReports 请求/响应 =====

    /** E-ANA-03 STEP-ANA-02 三报表：①sessions×[source,medium] ②sessions×[device] ③eventCount×[eventName] 五事件过滤 */
    private String buildBatchRequestBody(RangeWindow range) throws Exception {
        Map<String, Object> dateRange = Map.of("startDate", range.ga4StartDate(), "endDate", "today");

        Map<String, Object> sourceReport = new HashMap<>();
        sourceReport.put("dateRanges", List.of(dateRange));
        sourceReport.put("dimensions", List.of(Map.of("name", "sessionSource"), Map.of("name", "sessionMedium")));
        sourceReport.put("metrics", List.of(Map.of("name", "sessions")));

        Map<String, Object> deviceReport = new HashMap<>();
        deviceReport.put("dateRanges", List.of(dateRange));
        deviceReport.put("dimensions", List.of(Map.of("name", "deviceCategory")));
        deviceReport.put("metrics", List.of(Map.of("name", "sessions")));

        Map<String, Object> funnelReport = new HashMap<>();
        funnelReport.put("dateRanges", List.of(dateRange));
        funnelReport.put("dimensions", List.of(Map.of("name", "eventName")));
        funnelReport.put("metrics", List.of(Map.of("name", "eventCount")));
        funnelReport.put("dimensionFilter", Map.of("filter", Map.of(
                "fieldName", "eventName",
                "inListFilter", Map.of("values", Ga4Normalizer.FUNNEL_STAGES))));

        return objectMapper.writeValueAsString(Map.of(
                "requests", List.of(sourceReport, deviceReport, funnelReport)));
    }

    private Ga4TrafficRaw parseBatchResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode reports = root.path("reports");

        List<Ga4TrafficRaw.SourceRow> sourceRows = new ArrayList<>();
        for (JsonNode row : reports.path(0).path("rows")) {
            sourceRows.add(new Ga4TrafficRaw.SourceRow(
                    dim(row, 0), dim(row, 1), metric(row)));
        }
        List<Ga4TrafficRaw.DeviceRow> deviceRows = new ArrayList<>();
        for (JsonNode row : reports.path(1).path("rows")) {
            deviceRows.add(new Ga4TrafficRaw.DeviceRow(dim(row, 0), metric(row)));
        }
        Map<String, Long> eventCounts = new HashMap<>();
        for (JsonNode row : reports.path(2).path("rows")) {
            eventCounts.put(dim(row, 0), metric(row));
        }
        return new Ga4TrafficRaw(sourceRows, deviceRows, eventCounts);
    }

    private String dim(JsonNode row, int index) {
        return row.path("dimensionValues").path(index).path("value").asText();
    }

    private long metric(JsonNode row) {
        return row.path("metricValues").path(0).path("value").asLong(0);
    }
}
