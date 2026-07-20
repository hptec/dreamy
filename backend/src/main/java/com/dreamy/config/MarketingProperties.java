package com.dreamy.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * marketing 域配置（DEC-MKT-3：expiring 阈值 = end_at − now ≤ 72h，配置项缺省 72）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.marketing")
public class MarketingProperties {

    /** coupon active→expiring 阈值（小时，DEC-MKT-3） */
    private int couponExpiringHours = 72;

    /** newsletter 退订 token HMAC 密钥（env NEWSLETTER_UNSUBSCRIBE_SECRET）。轮换即作废旧链接，下期营销邮件补发新链接即可 */
    private String unsubscribeSecret = "";

    /** newsletter 退订 token 有效期（秒，缺省 30 天） */
    private long unsubscribeTokenTtlSeconds = 30L * 24 * 3600;

    @PostConstruct
    void validateUnsubscribeSecret() {
        // 启动期 fail-fast：Gradle/IDE/测试/生产任意启动路径统一强制，不只靠 backend-api.sh。
        // 对齐 JwtTokenProvider 标准：拒绝 blank（纯空白不可）且按 UTF-8 字节数 ≥32。
        if (unsubscribeSecret == null || unsubscribeSecret.isBlank()
                || unsubscribeSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "NEWSLETTER_UNSUBSCRIBE_SECRET must be configured with at least 32 UTF-8 bytes (non-blank, high entropy)");
        }
        // TTL 上限 10 年，×1000 不溢出 long
        if (unsubscribeTokenTtlSeconds <= 0 || unsubscribeTokenTtlSeconds > 10L * 365 * 24 * 3600) {
            throw new IllegalStateException(
                    "dreamy.marketing.unsubscribe-token-ttl-seconds must be within (0, 315360000]");
        }
    }
}
