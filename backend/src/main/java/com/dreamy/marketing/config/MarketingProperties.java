package com.dreamy.marketing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * marketing 域配置（DEC-MKT-3：expiring 阈值 = end_at − now ≤ 72h，配置项缺省 72）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.marketing")
public class MarketingProperties {

    /** coupon active→expiring 阈值（小时，DEC-MKT-3） */
    private int couponExpiringHours = 72;
}
