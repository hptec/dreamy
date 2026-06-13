package com.dreamy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;

/**
 * 异步任务配置。
 * 用于 CDN 缓存清除等异步操作。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "cdnInvalidationExecutor")
    public Executor cdnInvalidationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cdn-invalidation-");
        executor.initialize();
        return executor;
    }
}
