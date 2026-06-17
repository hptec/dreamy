package com.dreamy.infra.mail;

import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.user.repository.UserMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * q.mail 消费基建端口装配（决策 3 进程内直调防腐层；与 ReviewPortConfig 同范式）。
 * CustomerEmailPort：基于 identity UserMapper 的只读适配（identity 域后续提供真实 bean 自动让位）；
 * 已匿名化用户（anonymized=1）邮箱已脱敏不可达，返回 null 由消费侧跳过。
 */
@Configuration
public class MailPortConfig {

    @Bean
    @ConditionalOnMissingBean(CustomerEmailPort.class)
    public CustomerEmailPort customerEmailPortAdapter(UserMapper userMapper) {
        return new CustomerEmailPort() {
            @Override
            public String getEmail(Long customerId) {
                User user = loadActive(customerId);
                return user == null ? null : user.getEmail();
            }

            @Override
            public String getLocalePref(Long customerId) {
                User user = loadActive(customerId);
                return user == null ? null : user.getLocalePref();
            }

            private User loadActive(Long customerId) {
                if (customerId == null) {
                    return null;
                }
                User user = userMapper.selectById(customerId);
                if (user == null || Boolean.TRUE.equals(user.getAnonymized())) {
                    return null;
                }
                return user;
            }
        };
    }
}
