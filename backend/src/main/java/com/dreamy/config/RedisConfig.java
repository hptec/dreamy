package com.dreamy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置。提供 StringRedisTemplate 用于：
 * - 会话有效性单级提示缓存 store:session:valid:{tokenId}（最终授权始终以 DB 为准）
 * - OTP 频控窗口 otp:resend / otp:count:email|ip（rate_limit 契约）
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
