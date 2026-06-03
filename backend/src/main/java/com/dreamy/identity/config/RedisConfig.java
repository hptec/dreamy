package com.dreamy.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置。提供 StringRedisTemplate 用于：
 * - 会话有效性单级缓存 store:session:valid:{tokenId}（QP-003 / EDGE-023 强一致全集群）
 * - OTP 频控窗口 otp:resend / otp:count:email|ip（rate_limit 契约）
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
