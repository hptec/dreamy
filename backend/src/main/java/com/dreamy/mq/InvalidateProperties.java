package com.dreamy.mq;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * q.invalidate 失效消费者下游配置（EVT-MKT-002 / 决策 22）。
 * - next-internal-url：Next.js standalone 内部地址（POST /api/revalidate，仅内网）；
 * - revalidate-token：共享密钥（header x-revalidate-token）；
 * - site-base-url：店面公网基址（Cloudflare purge 完整 URL 拼装）；
 * - mode：stub（dev 缺省，仅日志不出网——与 mq/storage/stripe stub 惯例一致）| real（HTTP 直调，失败抛出 → nack）；
 * - cloudflare.*：zone token 仅后端配置（error-strategy 密钥面），purge 失败重试期间 CDN 靠 s-maxage TTL + serve-stale 兜底。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.invalidate")
public class InvalidateProperties {

    /** stub | real（revalidate + purge 双步共用开关；stub 仅日志） */
    private String mode = "stub";

    /** Next.js 内部地址（决策 22 standalone 同源） */
    private String nextInternalUrl = "http://localhost:5173";

    /** x-revalidate-token 共享密钥 */
    private String revalidateToken = "";

    /** 店面公网基址（purge URL = site-base-url + localized path） */
    private String siteBaseUrl = "http://localhost:5173";

    /** 下游 HTTP 超时（毫秒） */
    private int timeoutMs = 5000;

    private Cloudflare cloudflare = new Cloudflare();

    @Data
    public static class Cloudflare {
        /** zone id（real 模式必填） */
        private String zoneId = "";
        /** API token（仅后端配置，不落日志） */
        private String apiToken = "";
        /** purge API 基址（测试可指向 mock） */
        private String apiBaseUrl = "https://api.cloudflare.com/client/v4";
    }

    public boolean isReal() {
        return "real".equalsIgnoreCase(mode);
    }

    @PostConstruct
    void validateRealModeToken() {
        if (isReal() && (revalidateToken == null || revalidateToken.isBlank())) {
            throw new IllegalStateException("REVALIDATE_TOKEN must be configured when INVALIDATE_MODE=real");
        }
    }
}
