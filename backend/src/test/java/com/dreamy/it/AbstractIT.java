package com.dreamy.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.dreamy.DreamyApplication;
import com.dreamy.infra.grpc.AuditGateClient;
import com.dreamy.infra.grpc.IdentityGateClient;
import com.dreamy.infra.grpc.TemplateGateClient;

/**
 * 集成测试基类：真 MySQL + Redis（Testcontainers，零 Mock）。
 * 表结构由 huihao-mysql DDL-auto（@EnableMysql auto=update）从实体自动建立，不再挂载 schema.sql。
 *
 * 2026-09-17 Java 删码：身份基线种子（权限字典/超管/auth_config）已迁 Rust server bootstrap（IT 不起 Rust）；
 * 身份域全部读/写经 IdentityGate gRPC——IT 环境无 Rust server，mock 三个 client：
 * IdentityGate（ensureDemoUser 返回递增 id 供业务种子引用）、TemplateGate（getTemplate 返回 empty，
 * 对齐「模板缺失 → subject=code、body=""」语义）、AuditGate（record no-op，best-effort）。
 */
@SpringBootTest(classes = {DreamyApplication.class, AbstractIT.IdentityGateStubConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
public abstract class AbstractIT {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected TemplateGateClient templateGateClient;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected AuditGateClient auditGateClient;

    /**
     * IdentityGate stub 必须在 context 启动期就位——种子类监听 ApplicationReadyEvent,
     * 比 @BeforeEach 更早执行;stub 缺位时 Mockito 默认返回 0 导致业务种子撞唯一键。
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class IdentityGateStubConfig {
        @Bean
        IdentityGateClient identityGateClient() {
            IdentityGateClient mock = org.mockito.Mockito.mock(IdentityGateClient.class);
            java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(1000);
            org.mockito.Mockito.when(mock.ensureDemoUser(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(inv -> seq.incrementAndGet());
            return mock;
        }
    }

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
    }
}
