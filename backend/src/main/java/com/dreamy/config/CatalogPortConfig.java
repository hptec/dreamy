package com.dreamy.config;

import com.dreamy.port.ReviewQueryPort;
import com.dreamy.port.TradingQueryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 跨域查询端口 stub 装配（任务书第 5 条：trading 域 sumPaidQty 端口未实现前定义接口 + stub，
 * @ConditionalOnMissingBean——trading/review 域提供真实 bean 后自动让位）。
 * stub 返回零值：EVT-CAT-001/002 覆盖写为 0/空（可重入，真实端口接入后下次事件/定时刷新即矫正）。
 */
@Configuration
public class CatalogPortConfig {

    private static final Logger log = LoggerFactory.getLogger(CatalogPortConfig.class);

    @Bean
    @ConditionalOnMissingBean(TradingQueryPort.class)
    public TradingQueryPort stubTradingQueryPort() {
        log.info("[CATALOG] TradingQueryPort stub active (trading 域未就绪，sumPaidQty 恒 0)");
        return new TradingQueryPort() {
            @Override
            public int sumPaidQty(Long productId, LocalDateTime since) {
                return 0;
            }

            @Override
            public List<Long> listPaidProductIds(LocalDateTime since) {
                return List.of();
            }

            @Override
            public Map<Long, Integer> sumSalesTotalByProductIds(Collection<Long> productIds) {
                // RM-CAT-01c：stub 空映射 → 调用方合并为 sales_total = 0
                return Map.of();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ReviewQueryPort.class)
    public ReviewQueryPort stubReviewQueryPort() {
        log.info("[CATALOG] ReviewQueryPort stub active (review 域未就绪，评分聚合恒空)");
        return productId -> new ReviewQueryPort.RatingSummary(BigDecimal.ZERO, 0);
    }
}
