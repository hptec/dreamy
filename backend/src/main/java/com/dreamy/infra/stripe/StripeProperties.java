package com.dreamy.infra.stripe;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stripe 集成配置（tech-profile integrations.payment，决策 7/25）。
 * 密钥全部配置化（环境变量注入）；secret/webhook secret 完全不落日志（error-strategy 脱敏规则）。
 * mode=stub（dev 缺省，沿用 identity oidc/smtp stub 风格）/ real（沙箱/生产）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.stripe")
public class StripeProperties {

    /** stub | real（dev 缺省 stub，零外部依赖可启动） */
    private String mode = "stub";

    /** Stripe Secret Key（sk_test_* 沙箱 / sk_live_*）；仅后端配置 */
    private String secretKey = "";

    /** webhook 签名密钥（whsec_*）；仅后端配置（webhook 安全第 1 条） */
    private String webhookSecret = "";

    /** Stripe API base */
    private String apiBase = "https://api.stripe.com";

    /** 调用超时（trading §0 StripePort：超时 10s → 504601） */
    private long timeoutMs = 10_000L;

    /** webhook 时间戳容差秒数（防重放窗口） */
    private long webhookToleranceSeconds = 300L;

    @PostConstruct
    void validateRealModeSecrets() {
        if (!"real".equalsIgnoreCase(mode)) {
            return;
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("STRIPE_SECRET_KEY must be configured when STRIPE_MODE=real");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET must be configured when STRIPE_MODE=real");
        }
    }
}
