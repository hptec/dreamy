package com.dreamy.infra.stripe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stripe webhook 签名验证单元测试（webhook 安全第 1 条 / V-TRD-028）：
 * HMAC-SHA256("<t>.<payload>") + v1 比对 + 时间戳容差；失败场景一律 false（调用方 401 401601 不读负载）。
 */
class StripeSignatureVerifierTest {

    private static final String SECRET = "whsec_test_secret";

    private StripeSignatureVerifier verifier;
    private long now;

    @BeforeEach
    void setUp() {
        StripeProperties props = new StripeProperties();
        props.setWebhookSecret(SECRET);
        props.setWebhookToleranceSeconds(300);
        verifier = new StripeSignatureVerifier(props);
        now = Instant.now().getEpochSecond();
    }

    private String sign(String secret, long timestamp, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("合法签名（t + v1）验签通过")
    void validSignaturePasses() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";
        String header = "t=" + now + ",v1=" + sign(SECRET, now, payload);
        assertThat(verifier.verify(payload, header, now)).isTrue();
    }

    @Test
    @DisplayName("负载被篡改 → 拒绝")
    void tamperedPayloadRejected() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"amount\":100}";
        String header = "t=" + now + ",v1=" + sign(SECRET, now, payload);
        assertThat(verifier.verify("{\"id\":\"evt_1\",\"amount\":999999}", header, now)).isFalse();
    }

    @Test
    @DisplayName("密钥不符 → 拒绝")
    void wrongSecretRejected() throws Exception {
        String payload = "{}";
        String header = "t=" + now + ",v1=" + sign("whsec_other", now, payload);
        assertThat(verifier.verify(payload, header, now)).isFalse();
    }

    @Test
    @DisplayName("时间戳超出容差（防重放）→ 拒绝")
    void staleTimestampRejected() throws Exception {
        long stale = now - 301;
        String payload = "{}";
        String header = "t=" + stale + ",v1=" + sign(SECRET, stale, payload);
        assertThat(verifier.verify(payload, header, now)).isFalse();
    }

    @Test
    @DisplayName("多 v1 签名（密钥轮换）任一命中即通过")
    void multipleSignaturesAnyMatch() throws Exception {
        String payload = "{\"id\":\"evt_2\"}";
        String header = "t=" + now + ",v1=" + sign("whsec_rotated_out", now, payload)
                + ",v1=" + sign(SECRET, now, payload);
        assertThat(verifier.verify(payload, header, now)).isTrue();
    }

    @Test
    @DisplayName("头缺失/格式非法/缺 t/缺 v1 → 拒绝")
    void malformedHeaderRejected() {
        assertThat(verifier.verify("{}", null, now)).isFalse();
        assertThat(verifier.verify("{}", "", now)).isFalse();
        assertThat(verifier.verify("{}", "v1=abc", now)).isFalse();
        assertThat(verifier.verify("{}", "t=" + now, now)).isFalse();
        assertThat(verifier.verify("{}", "t=not-a-number,v1=abc", now)).isFalse();
        assertThat(verifier.verify(null, "t=" + now + ",v1=abc", now)).isFalse();
    }

    @Test
    @DisplayName("webhook secret 未配置 → fail-closed 拒绝")
    void missingSecretFailClosed() throws Exception {
        StripeProperties empty = new StripeProperties();
        empty.setWebhookSecret("");
        StripeSignatureVerifier unconfigured = new StripeSignatureVerifier(empty);
        String payload = "{}";
        String header = "t=" + now + ",v1=" + sign(SECRET, now, payload);
        assertThat(unconfigured.verify(payload, header, now)).isFalse();
    }
}
