package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.enums.ProductStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.repository.ProductMapper;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.user.repository.UserMapper;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.port.ReviewIdentityQueryPort;
import com.dreamy.port.TradingPurchaseQueryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * review 域跨域端口装配（决策 3：进程内直调防腐层，禁止跨域直查表语义在端口实现内收口）。
 * - TradingPurchaseQueryPort：trading 域并行实现中，本域 stub 兜底（@ConditionalOnMissingBean）。
 *   **fail-closed 恒 false**——403801 为越权防护（s-756/s-762），端口缺席必须拒绝评价提交而非放行。
 * - ReviewCatalogSnapshotPort / ReviewIdentityQueryPort：基于 catalog/identity 既有 Mapper 的只读适配
 *   （提供方后续给出真实 bean 自动让位，与 marketing CatalogQueryPort 同范式）。
 */
@Configuration
public class ReviewPortConfig {

    private static final Logger log = LoggerFactory.getLogger(ReviewPortConfig.class);

    @Bean
    @ConditionalOnMissingBean(TradingPurchaseQueryPort.class)
    public TradingPurchaseQueryPort stubTradingPurchaseQueryPort() {
        log.info("[REVIEW] TradingPurchaseQueryPort stub active (trading 域未就绪，fail-closed 恒 false → 403801)");
        return (customerId, productId) -> false;
    }

    @Bean
    @ConditionalOnMissingBean(ReviewCatalogSnapshotPort.class)
    public ReviewCatalogSnapshotPort reviewCatalogSnapshotPortAdapter(ProductMapper productMapper) {
        return new ReviewCatalogSnapshotPort() {
            @Override
            public ProductBrief getProductBrief(Long productId) {
                if (productId == null) {
                    return null;
                }
                Product product = productMapper.selectById(productId);
                return product == null ? null : toBrief(product);
            }

            @Override
            public Map<Long, ProductBrief> getProductBriefs(Collection<Long> productIds) {
                Map<Long, ProductBrief> result = new LinkedHashMap<>();
                if (productIds == null || productIds.isEmpty()) {
                    return result;
                }
                for (Product product : productMapper.selectList(
                        new LambdaQueryWrapper<Product>().in(Product::getId, productIds))) {
                    result.put(product.getId(), toBrief(product));
                }
                return result;
            }

            private ProductBrief toBrief(Product product) {
                return new ProductBrief(product.getId(), product.getSlug(), product.getName(),
                        product.getStatus() == ProductStatus.PUBLISHED);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ReviewIdentityQueryPort.class)
    public ReviewIdentityQueryPort identityQueryPortAdapter(UserMapper userMapper) {
        return userId -> {
            if (userId == null) {
                return null;
            }
            User user = userMapper.selectById(userId);
            return user == null ? null : user.getName();
        };
    }
}
