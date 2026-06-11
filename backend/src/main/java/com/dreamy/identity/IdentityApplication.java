package com.dreamy.identity;

import huihao.mysql.annotation.EnableMysql;
import huihao.redis.EnableHuiHaoRedis;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * identity-auth 单一 Spring Boot 入口（端口 8080）。
 * 同时装配 store(/api/store/*) 与 admin(/api/admin/*) 表现层 + 各 domain 领域/基础设施。
 * 约束: 单一 @SpringBootApplication 入口；BE-DIM-6 双 JWT 过滤器按前缀选解析器。
 * huihao-mysql DDL-auto（@EnableMysql）+ huihao-redis 分布式锁（@EnableHuiHaoRedis）。
 * portal-api-integration：扫描根扩为 com.dreamy（公共基建 com.dreamy.infra.* 与后续七域包平级装配）。
 */
@SpringBootApplication(scanBasePackages = "com.dreamy")
@EnableConfigurationProperties
@EnableMysql(auto = "update", scanPackages = {"com.dreamy.identity.domain", "com.dreamy.catalog.domain",
        "com.dreamy.shipping.domain", "com.dreamy.review.domain", "com.dreamy.marketing.domain",
        "com.dreamy.showroom.domain", "com.dreamy.trading.domain", "com.dreamy.infra.mail"})
@EnableHuiHaoRedis
@MapperScan({"com.dreamy.identity.domain.*.repository", "com.dreamy.catalog.domain.*.repository",
        "com.dreamy.shipping.domain.*.repository", "com.dreamy.analytics.repository",
        "com.dreamy.review.domain.*.repository", "com.dreamy.marketing.domain.*.repository",
        "com.dreamy.showroom.domain.*.repository", "com.dreamy.trading.domain.*.repository",
        "com.dreamy.infra.mail.repository"})
@EnableScheduling
@EnableAsync
public class IdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
