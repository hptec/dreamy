package com.dreamy.identity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * identity-auth-fullstack 单一 Spring Boot 入口（端口 8080）。
 * 同时装配 store(/api/store/*) 与 admin(/api/admin/*) 表现层 + common 领域/基础设施。
 * 约束: code-structure-spec 单一 @SpringBootApplication 入口；BE-DIM-6 双 JWT 过滤器按前缀选解析器。
 */
@SpringBootApplication(scanBasePackages = "com.dreamy.identity")
@MapperScan("com.dreamy.identity.common.repository.mapper")
@EnableScheduling
@EnableAsync
public class IdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
