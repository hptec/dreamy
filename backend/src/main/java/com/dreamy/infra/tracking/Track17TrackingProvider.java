package com.dreamy.infra.tracking;

import com.dreamy.domain.shipment.entity.Shipment;
import com.dreamy.enums.ShipmentStatus;
import com.dreamy.port.TrackingProviderPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 17TRACK v2.2 轨迹供应商（order-flow-complete D）：
 * POST {base}/register [{number, carrier}] 与 POST {base}/gettrackinfo [{number, carrier}]，header 17token，超时 5s。
 * 状态映射（latest_status.status / events[].stage）：InfoReceived→PENDING，InTransit→IN_TRANSIT，
 * OutForDelivery→OUT_FOR_DELIVERY，Delivered→DELIVERED，Exception/Undelivered/Expired→EXCEPTION。
 */
@Component
@ConditionalOnProperty(name = "dreamy.tracking.mode", havingValue = "track17")
public class Track17TrackingProvider implements TrackingProviderPort {

    private static final Logger log = LoggerFactory.getLogger(Track17TrackingProvider.class);

    /** 承运商 code → 17TRACK carrier key */
    static final Map<String, Integer> CARRIER_KEYS = Map.of(
            "DHL", 100001,
            "UPS", 100002,
            "FEDEX", 100003,
            "USPS", 21051);

    private final TrackingProperties properties;
    private final HttpClient http;
    private final ObjectMapper objectMapper;

    public Track17TrackingProvider(TrackingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getProviderTimeoutMs()))
                .build();
        if (properties.getTrack17ApiKey() == null || properties.getTrack17ApiKey().isBlank()) {
            throw new IllegalStateException("TRACK17_API_KEY must be configured when TRACKING_MODE=track17");
        }
        log.info("[TRACKING] 17TRACK provider active base={}", properties.getTrack17BaseUrl());
    }

    @Override
    public String name() {
        return "track17";
    }

    @Override
    public String register(Shipment shipment) {
        JsonNode root = post("/register", body(shipment));
        JsonNode accepted = root.path("data").path("accepted");
        if (accepted.isArray() && !accepted.isEmpty()) {
            return shipment.getTrackingNo();
        }
        JsonNode rejected = root.path("data").path("rejected");
        if (rejected.isArray() && !rejected.isEmpty()) {
            JsonNode error = rejected.get(0).path("error");
            // -18019901 = 已登记，视为成功
            if (error.path("code").asInt() == -18019901) {
                return shipment.getTrackingNo();
            }
            throw new ProviderException("17track register rejected: " + error.path("message").asText(), null);
        }
        return null;
    }

    @Override
    public List<ProviderEvent> fetchEvents(Shipment shipment) {
        JsonNode root = post("/gettrackinfo", body(shipment));
        JsonNode accepted = root.path("data").path("accepted");
        if (!accepted.isArray() || accepted.isEmpty()) {
            JsonNode rejected = root.path("data").path("rejected");
            if (rejected.isArray() && !rejected.isEmpty()) {
                throw new ProviderException("17track gettrackinfo rejected: "
                        + rejected.get(0).path("error").path("message").asText(), null);
            }
            return List.of();
        }
        return parseEvents(accepted.get(0));
    }

    List<ProviderEvent> parseEvents(JsonNode accepted) {
        List<ProviderEvent> events = new ArrayList<>();
        JsonNode providers = accepted.path("track_info").path("tracking").path("providers");
        if (providers.isArray()) {
            for (JsonNode provider : providers) {
                for (JsonNode e : provider.path("events")) {
                    LocalDateTime at = parseTime(e.path("time_iso").asText(null), e.path("time_utc").asText(null));
                    ShipmentStatus status = mapStatus(e.path("stage").asText(null), e.path("sub_status").asText(null));
                    String location = e.path("location").asText(null);
                    String description = e.path("description").asText(null);
                    events.add(new ProviderEvent(null, at == null ? LocalDateTime.now() : at,
                            status == null ? ShipmentStatus.IN_TRANSIT : status, location, description));
                }
            }
        }
        // 最新状态兜底：无事件但 latest_status 为 Delivered 时补一条系统事件
        if (events.isEmpty()) {
            ShipmentStatus latest = mapStatus(accepted.path("track_info").path("latest_status").path("status").asText(null), null);
            if (latest != null && latest != ShipmentStatus.PENDING) {
                events.add(new ProviderEvent(null, LocalDateTime.now(), latest, null, "Status: " + latest.name()));
            }
        }
        return events;
    }

    static ShipmentStatus mapStatus(String stage, String subStatus) {
        String s = stage == null ? (subStatus == null ? "" : subStatus) : stage;
        String upper = s.toUpperCase(Locale.ROOT);
        if (upper.startsWith("INFORECEIVED") || upper.startsWith("NOTFOUND")) {
            return ShipmentStatus.PENDING;
        }
        if (upper.startsWith("INTRANSIT") || upper.startsWith("PICKEDUP") || upper.startsWith("DEPARTURE")
                || upper.startsWith("ARRIVAL") || upper.startsWith("AVAILABLEFORPICKUP")) {
            return ShipmentStatus.IN_TRANSIT;
        }
        if (upper.startsWith("OUTFORDELIVERY")) {
            return ShipmentStatus.OUT_FOR_DELIVERY;
        }
        if (upper.startsWith("DELIVERED")) {
            return ShipmentStatus.DELIVERED;
        }
        if (upper.startsWith("EXCEPTION") || upper.startsWith("UNDELIVERED") || upper.startsWith("EXPIRED")
                || upper.startsWith("DELIVERYFAILURE") || upper.startsWith("RETURN")) {
            return ShipmentStatus.EXCEPTION;
        }
        return null;
    }

    private static LocalDateTime parseTime(String iso, String utc) {
        try {
            if (iso != null && !iso.isBlank()) {
                return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            }
            if (utc != null && !utc.isBlank()) {
                return OffsetDateTime.parse(utc).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private String body(Shipment shipment) {
        Integer key = shipment.getCarrierCode() == null ? null
                : CARRIER_KEYS.get(shipment.getCarrierCode().toUpperCase(Locale.ROOT));
        return key == null
                ? "[{\"number\":\"" + escape(shipment.getTrackingNo()) + "\"}]"
                : "[{\"number\":\"" + escape(shipment.getTrackingNo()) + "\",\"carrier\":" + key + "}]";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private JsonNode post(String path, String json) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getTrack17BaseUrl() + path))
                .timeout(Duration.ofMillis(properties.getProviderTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("17token", properties.getTrack17ApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ProviderException("17track http " + response.statusCode(), null);
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(0) != 0) {
                throw new ProviderException("17track code " + root.path("code").asInt(), null);
            }
            return root;
        } catch (ProviderException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderException("17track interrupted", ex);
        } catch (Exception ex) {
            throw new ProviderException("17track call failed: " + ex.getMessage(), ex);
        }
    }
}
