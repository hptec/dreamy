package com.dreamy.domain.subscriber.service;

import com.dreamy.config.MarketingProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * Newsletter 退订 token 服务（方案 A 邮件退订链接）。
 * 格式 5 段：u1.&lt;b64url(email)&gt;.&lt;b64url(expEpochMillis)&gt;.&lt;b64url(genEpochMillis)&gt;.&lt;b64url(sig)&gt;。
 * 签名输入 = "newsletter-unsubscribe\n" + 前 4 段原文（UTF-8），HMAC-SHA256，域分离防跨用途重放。
 * gen = 生成时刻持久化 subscribed_at 的 epoch 毫秒（UTC，对齐 DATETIME(3)）：复活后的新订阅
 * subscribed_at 更新，旧 token 的 gen 落后 → SQL 谓词原子判定失效，防"旧链接退订新订阅"竞态。
 * 一切畸形/过期/签名不符统一抛 InvalidTokenException（不区分原因，防侧信道）。
 */
@Service
public class UnsubscribeTokenService {

    /** 签名域分离前缀 */
    private static final String PURPOSE = "newsletter-unsubscribe";
    private static final String VERSION = "u1";
    /** 解析防御：token 总长上限 */
    private static final int MAX_TOKEN_LENGTH = 1024;
    private static final int MAX_EMAIL_LENGTH = 255;

    private final MarketingProperties properties;
    private final Clock clock;

    public UnsubscribeTokenService(MarketingProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 解析结果 */
    public record ParsedToken(String email, long expEpochMillis, long genEpochMillis) {
    }

    /** 统一无效 token 异常（畸形/过期/签名不符不分原因） */
    public static class InvalidTokenException extends RuntimeException {
    }

    /** LocalDateTime（DB 口径 UTC）→ epoch 毫秒 */
    public static long toEpochMillis(LocalDateTime dbTime) {
        return dbTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** 生成退订 token（gen 取持久化 subscribed_at 的 epoch 毫秒） */
    public String generate(String email, long genEpochMillis) {
        long exp = clock.millis() + properties.getUnsubscribeTokenTtlSeconds() * 1000;
        String segEmail = b64(email.getBytes(StandardCharsets.UTF_8));
        String segExp = b64(Long.toString(exp).getBytes(StandardCharsets.US_ASCII));
        String segGen = b64(Long.toString(genEpochMillis).getBytes(StandardCharsets.US_ASCII));
        String payload = VERSION + "." + segEmail + "." + segExp + "." + segGen;
        return payload + "." + b64(sign(payload));
    }

    /** 解析并校验 token；任何问题一律 InvalidTokenException */
    public ParsedToken parse(String token) {
        try {
            if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
                throw new InvalidTokenException();
            }
            String[] parts = token.split("\\.", -1);
            if (parts.length != 5 || !VERSION.equals(parts[0])) {
                throw new InvalidTokenException();
            }
            byte[] emailBytes = unb64(parts[1]);
            if (emailBytes.length == 0 || emailBytes.length > MAX_EMAIL_LENGTH) {
                throw new InvalidTokenException();
            }
            long exp = parseDigits(unb64(parts[2]));
            long gen = parseDigits(unb64(parts[3]));
            byte[] sig = unb64(parts[4]);
            String payload = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
            if (!MessageDigest.isEqual(sig, sign(payload))) {
                throw new InvalidTokenException();
            }
            if (exp <= clock.millis()) {
                throw new InvalidTokenException();
            }
            return new ParsedToken(new String(emailBytes, StandardCharsets.UTF_8), exp, gen);
        } catch (InvalidTokenException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidTokenException();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getUnsubscribeSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((PURPOSE + "\n").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long parseDigits(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 19) {
            throw new InvalidTokenException();
        }
        long value = 0;
        for (byte b : bytes) {
            if (b < '0' || b > '9') {
                throw new InvalidTokenException();
            }
            value = value * 10 + (b - '0');
        }
        return value;
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] unb64(String segment) {
        try {
            return Base64.getUrlDecoder().decode(segment);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }
}
