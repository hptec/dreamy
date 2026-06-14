package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.enums.ImageKind;
import com.dreamy.enums.ProductStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.ProductImage;
import com.dreamy.domain.product.entity.ProductTranslation;
import com.dreamy.domain.product.entity.Sku;
import com.dreamy.domain.product.repository.ProductImageRepository;
import com.dreamy.domain.product.repository.ProductMapper;
import com.dreamy.domain.product.repository.ProductTranslationRepository;
import com.dreamy.domain.product.repository.SkuMapper;
import com.dreamy.port.TradingCatalogSnapshotPort;
import com.dreamy.port.TradingDyeLotPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * trading 域跨域端口装配（决策 3：进程内直调防腐层，禁止跨域直查表语义在端口实现内收口）。
 * - TradingCatalogSnapshotPort：基于 catalog 既有 Mapper/Repository 的只读适配（catalog 域后续提供
 *   真实 bean 自动让位，@ConditionalOnMissingBean——与 review/marketing 端口同范式）。
 *   locale 文案解析：es/fr 命中 product_translation 覆盖，缺翻译回退 EN；主图 = gallery 最小 sort。
 * - TradingDyeLotPort：showroom 域并行实现中，本域 stub 兜底（恒空数组——getCart.STEP-TRD-03 口径，决策 20.4）。
 */
@Configuration
public class TradingPortConfig {

    private static final Logger log = LoggerFactory.getLogger(TradingPortConfig.class);

    @Bean
    @ConditionalOnMissingBean(TradingDyeLotPort.class)
    public TradingDyeLotPort stubDyeLotPort() {
        log.info("[TRADING] TradingDyeLotPort stub active (showroom 域未就绪，dye_lot_product_ids 恒空)");
        return (customerId, productIds) -> List.of();
    }

    @Bean
    @ConditionalOnMissingBean(TradingCatalogSnapshotPort.class)
    public TradingCatalogSnapshotPort tradingCatalogSnapshotPortAdapter(ProductMapper productMapper,
                                                                 ProductTranslationRepository translationRepository,
                                                                 ProductImageRepository imageRepository,
                                                                 SkuMapper skuMapper) {
        return new TradingCatalogSnapshotPort() {
            @Override
            public ProductBrief getProductBrief(Long productId, String locale) {
                if (productId == null) {
                    return null;
                }
                return getProductBriefs(List.of(productId), locale).get(productId);
            }

            @Override
            public Map<Long, ProductBrief> getProductBriefs(Collection<Long> productIds, String locale) {
                Map<Long, ProductBrief> result = new LinkedHashMap<>();
                if (productIds == null || productIds.isEmpty()) {
                    return result;
                }
                List<Product> products = productMapper.selectList(
                        new LambdaQueryWrapper<Product>().in(Product::getId, productIds));
                if (products.isEmpty()) {
                    return result;
                }
                List<Long> ids = products.stream().map(Product::getId).toList();
                // locale 文案覆盖（决策 13：es/fr 命中翻译，缺翻译回退 EN）
                Map<Long, ProductTranslation> translations = new HashMap<>();
                if ("es".equals(locale) || "fr".equals(locale)) {
                    for (ProductTranslation t : translationRepository.listByProductIds(ids, locale)) {
                        translations.put(t.getProductId(), t);
                    }
                }
                // 主图派生：kind=gallery 最小 sort（CV-CAT-010）
                Map<Long, String> mainImages = new HashMap<>();
                imageRepository.listByProductIds(ids).stream()
                        .filter(img -> img.getKind() == ImageKind.GALLERY)
                        .sorted(Comparator.comparing(img -> img.getSort() == null ? 0 : img.getSort()))
                        .forEach(img -> mainImages.putIfAbsent(img.getProductId(), img.getUrl()));
                for (Product product : products) {
                    ProductTranslation t = translations.get(product.getId());
                    String name = t != null && t.getName() != null && !t.getName().isBlank()
                            ? t.getName() : product.getName();
                    result.put(product.getId(), new ProductBrief(product.getId(), product.getSlug(), name,
                            product.getPrice(), product.getCompareAt(),
                            product.getMultiCurrencyPrices(), mainImages.get(product.getId()),
                            product.getLeadTimeDays(), product.getRushAvailable(),
                            product.getCustomSizeAvailable(),
                            product.getStatus() == null ? ProductStatus.DRAFT.getKey() : product.getStatus().getKey()));
                }
                return result;
            }

            @Override
            public SkuBrief getSku(Long skuId) {
                if (skuId == null) {
                    return null;
                }
                Sku sku = skuMapper.selectById(skuId);
                return sku == null ? null : toSkuBrief(sku);
            }

            @Override
            public Map<Long, SkuBrief> getSkus(Collection<Long> skuIds) {
                Map<Long, SkuBrief> result = new LinkedHashMap<>();
                if (skuIds == null || skuIds.isEmpty()) {
                    return result;
                }
                for (Sku sku : skuMapper.selectList(new LambdaQueryWrapper<Sku>().in(Sku::getId, skuIds))) {
                    result.put(sku.getId(), toSkuBrief(sku));
                }
                return result;
            }

            private SkuBrief toSkuBrief(Sku sku) {
                return new SkuBrief(sku.getId(), sku.getProductId(), sku.getSkuCode(), sku.getColor(),
                        sku.getSize(), sku.getStock(), sku.getVersion());
            }
        };
    }
}
