package com.dreamy.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 双 JWT 配置（独立密钥/过期）。
 * 约束: shared-contracts jwt_isolation（store 2h+refresh30d / admin 8h 无 refresh；DR-01 禁止复用密钥）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "identity.jwt")
public class JwtProperties {

    private Store store = new Store();
    private Admin admin = new Admin();

    @Data
    public static class Store {
        /** STORE_JWT_SECRET 独立密钥 */
        private String secret;
        /** access TTL 秒（默认 2h=7200） */
        private long accessTtlSeconds = 7200;
        /** refresh TTL 秒（默认 30d=2592000，滑动可撤销） */
        private long refreshTtlSeconds = 2592000;
        private String issuer = "dreamy-store";
    }

    @Data
    public static class Admin {
        /** ADMIN_JWT_SECRET 独立密钥，禁止复用 store */
        private String secret;
        /** access TTL 秒（默认 8h=28800，无 refresh） */
        private long accessTtlSeconds = 28800;
        private String issuer = "dreamy-admin";
    }
}
