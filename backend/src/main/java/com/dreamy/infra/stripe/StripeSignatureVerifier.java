package com.dreamy.infra.stripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stripe webhook 签名验证（webhook 安全第 1 条，决策 7 / BE-DIM-4 / V-TRD-028）。
 * Stripe-Signature 头格式：`t=<unix_ts>,v1=<hmac_hex>[,v1=...]`；
 * 期望签名 = HMAC-SHA256(webhook_secret, "<t>.<payload>")，常数时间比较；
 * 时间戳容差（缺省 300s）防重放。验签失败调用方必须：不读取负载、不写任何业务数据、
 * 不落 processed_event，返回 401 `401601`（Stripe 按退避策略重投）。
 * 本类只接收已读出的原始 body 字符串做纯计算，不解析/不记录负载与签名内容（脱敏）。
 */
@Component
public class StripeSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(StripeSignatureVerifier.class);
    private static final String SCHEME_V1 = "v1";

    private final StripeProperties props;

    public StripeSignatureVerifier(StripeProperties props) {
        this.props = props;
    }

    /** 按当前时间验签 */
    public boolean verify(String payload, String signatureHeader) {
        return verify(payload, signatureHeader, Instant.now().getEpochSecond());
    }

    /** 可注入时钟的验签入口（单测用） */
    public boolean verify(String payload, String signatureHeader, long nowEpochSeconds) {
        if (payload == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String secret = props.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            // 未配置 webhook secret → fail-closed
            log.warn("[STRIPE-WEBHOOK] webhook secret not configured, reject");
            return false;
        }
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if ("t".equals(key)) {
                try {
                    timestamp = Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    return false;
                }
            } else if (SCHEME_V1.equals(key)) {
                signatures.add(value);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - timestamp) > props.getWebhookToleranceSeconds()) {
            return false;
        }
        byte[] expected = hmacSha256Hex(secret, timestamp + "." + payload)
                .getBytes(StandardCharsets.UTF_8);
        for (String candidate : signatures) {
            if (MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    private String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            // JDK 必备算法，不可达；fail-closed
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
