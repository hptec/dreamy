package com.dreamy;

import huihao.mysql.annotation.EnableMysql;
import huihao.redis.EnableHuiHaoRedis;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Dreamy 单一 Spring Boot 入口（端口 18081）。
 * 同时装配 store(/api/store/*) 与 admin(/api/admin/*) 表现层 + 各 domain 领域/基础设施。
 * 约束: 单一 @SpringBootApplication 入口；BE-DIM-6 双 JWT 过滤器按前缀选解析器。
 * huihao-mysql DDL-auto（@EnableMysql）+ huihao-redis 分布式锁（@EnableHuiHaoRedis）。
 */
@SpringBootApplication(scanBasePackages = "com.dreamy")
@EnableConfigurationProperties
@EnableMysql(auto = "update", scanPackages = {"com.dreamy.domain", "com.dreamy.infra.mail"})
@EnableHuiHaoRedis
@MapperScan({"com.dreamy.domain.*.repository", "com.dreamy.domain.dashboard.repository",
        "com.dreamy.infra.mail.repository"})
@EnableScheduling
@EnableAsync
public class DreamyApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamyApplication.class, args);
    }
}
