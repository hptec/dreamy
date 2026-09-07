package com.dreamy.infra.exchangerate;

import com.dreamy.port.ExchangeRateProviderPort;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frankfurter（ECB 参考汇率）供应商：GET {base}/latest?from=USD&to=EUR,GBP,CAD,AUD，超时 5s（§4.6）。
 * 响应形状 {"amount":1,"base":"USD","date":"2026-09-05","rates":{"AUD":1.5,...}}。
 */
@Component
@ConditionalOnProperty(name = "dreamy.exchange-rate.mode", havingValue = "frankfurter")
public class FrankfurterExchangeRateProvider implements ExchangeRateProviderPort {

    private static final Logger log = LoggerFactory.getLogger(FrankfurterExchangeRateProvider.class);

    private final ExchangeRateProperties properties;
    private final HttpClient http;
    private final ObjectMapper objectMapper;

    public FrankfurterExchangeRateProvider(ExchangeRateProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getProviderTimeoutMs()))
                .build();
        this.objectMapper = objectMapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        log.info("[EXRATE] Frankfurter provider active base={}", properties.getFrankfurterBaseUrl());
    }

    @Override
    public String name() {
        return "frankfurter";
    }

    @Override
    public RateQuote fetchLatest(String base, List<String> currencies) {
        String url = properties.getFrankfurterBaseUrl() + "/latest?from=" + base + "&to=" + String.join(",", currencies);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(properties.getProviderTimeoutMs()))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ProviderException("frankfurter http " + response.statusCode(), null);
            }
            return parse(response.body());
        } catch (ProviderException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderException("frankfurter interrupted", ex);
        } catch (Exception ex) {
            throw new ProviderException("frankfurter call failed: " + ex.getMessage(), ex);
        }
    }

    RateQuote parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            LocalDate date = root.hasNonNull("date") ? LocalDate.parse(root.get("date").asText()) : LocalDate.now();
            Map<String, BigDecimal> rates = new LinkedHashMap<>();
            JsonNode ratesNode = root.get("rates");
            if (ratesNode != null) {
                for (Iterator<Map.Entry<String, JsonNode>> it = ratesNode.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> e = it.next();
                    rates.put(e.getKey().toUpperCase(), new BigDecimal(e.getValue().asText()));
                }
            }
            if (rates.isEmpty()) {
                throw new ProviderException("frankfurter empty rates", null);
            }
            return new RateQuote(date, rates);
        } catch (ProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderException("frankfurter parse failed", ex);
        }
    }
}
