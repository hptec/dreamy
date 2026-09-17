package com.dreamy.infra.mail;

import com.dreamy.infra.grpc.CustomerInfoPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * q.mail 消费基建端口装配（决策 3 进程内直调防腐层；与 ReviewPortConfig 同范式）。
 * CustomerEmailPort：经 CustomerInfoPort(IdentityGate gRPC) 的只读适配；
 * 已匿名化用户（anonymized=1）邮箱已脱敏不可达，返回 null 由消费侧跳过。
 */
@Configuration
public class MailPortConfig {

    @Bean
    @ConditionalOnMissingBean(CustomerEmailPort.class)
    public CustomerEmailPort customerEmailPortAdapter(CustomerInfoPort customerInfoPort) {
        return new CustomerEmailPort() {
            @Override
            public String getEmail(Long customerId) {
                return customerInfoPort.byId(customerId)
                        .filter(info -> !info.isAnonymized())
                        .map(CustomerInfoPort.CustomerInfo::email)
                        .orElse(null);
            }

            @Override
            public String getLocalePref(Long customerId) {
                return customerInfoPort.byId(customerId)
                        .filter(info -> !info.isAnonymized())
                        .map(CustomerInfoPort.CustomerInfo::localePref)
                        .orElse(null);
            }
        };
    }
}
