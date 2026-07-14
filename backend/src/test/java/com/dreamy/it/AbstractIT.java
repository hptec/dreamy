package com.dreamy.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.dreamy.DreamyApplication;

/**
 * 集成测试基类：真 MySQL + Redis（Testcontainers，零 Mock）。
 * 表结构由 huihao-mysql DDL-auto（@EnableMysql auto=update）从实体自动建立，不再挂载 schema.sql。
 * 种子数据（权限字典/超管/auth_config）由主代码 DataInitializer 在应用启动时幂等写入。
 */
@SpringBootTest(classes = DreamyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
public abstract class AbstractIT {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("identity")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("rawtypes")
    static final GenericContainer REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // huihao-redis（Redisson）连到 Testcontainers Redis
        r.add("huihao.redis.host", REDIS::getHost);
        r.add("huihao.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("jetcache.remote.default.uri",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        // 集成测试使用隔离的临时数据库，显式开启演示种子；生产默认关闭。
        r.add("dreamy.seed.demo-enabled", () -> "true");
        r.add("dreamy.bootstrap-admin.email", () -> "admin@dreamy.test");
        r.add("dreamy.bootstrap-admin.password", () -> "TestAdmin@123456");
    }
}
