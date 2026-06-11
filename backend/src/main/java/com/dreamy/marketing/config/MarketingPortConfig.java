package com.dreamy.marketing.config;

import com.dreamy.catalog.domain.enums.ImageKind;
import com.dreamy.catalog.domain.enums.ProductStatus;
import com.dreamy.catalog.domain.product.entity.Product;
import com.dreamy.catalog.domain.product.entity.ProductImage;
import com.dreamy.catalog.domain.product.entity.ProductTranslation;
import com.dreamy.catalog.domain.product.repository.ProductImageRepository;
import com.dreamy.catalog.domain.product.repository.ProductRepository;
import com.dreamy.catalog.domain.product.repository.ProductTranslationRepository;
import com.dreamy.marketing.port.CatalogQueryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CatalogQueryPort 适配装配（任务书口径：catalog 域未提供 listProductRefs 同语义接口，
 * 本域定义 port + 基于 catalog Repository 的只读适配实现；@ConditionalOnMissingBean——
 * catalog 域后续提供真实 bean 自动让位）。
 * 语义（MAP-MKT-012 / CV-MKT-006）：仅 published 出 ProductRef，缺失/下架静默剔除；
 * name 按 locale 解析（product_translation 覆盖，缺翻译回退 EN）；image 取 gallery 首图。
 */
@Configuration
public class MarketingPortConfig {

    private static final Logger log = LoggerFactory.getLogger(MarketingPortConfig.class);

    @Bean
    @ConditionalOnMissingBean(CatalogQueryPort.class)
    public CatalogQueryPort catalogQueryPortAdapter(ProductRepository productRepository,
                                                    ProductTranslationRepository translationRepository,
                                                    ProductImageRepository imageRepository) {
        log.info("[MKT] CatalogQueryPort adapter active (read-only over catalog repositories)");
        return new CatalogQueryPort() {

            @Override
            public List<ProductRef> listProductRefs(Collection<Long> productIds, String locale) {
                if (productIds == null || productIds.isEmpty()) {
                    return List.of();
                }
                // 单次批查（NP-MKT-002）：商品 + 翻译 + 主图
                List<Product> products = productRepository.listByIds(productIds);
                List<Long> publishedIds = products.stream()
                        .filter(p -> p.getStatus() == ProductStatus.PUBLISHED)
                        .map(Product::getId).toList();
                if (publishedIds.isEmpty()) {
                    return List.of();
                }
                Map<Long, String> translatedNames = new HashMap<>();
                if ("es".equals(locale) || "fr".equals(locale)) {
                    for (ProductTranslation t : translationRepository.listByProductIds(publishedIds, locale)) {
                        if (t.getName() != null && !t.getName().isBlank()) {
                            translatedNames.put(t.getProductId(), t.getName());
                        }
                    }
                }
                Map<Long, String> mainImages = new HashMap<>();
                List<ProductImage> images = new ArrayList<>(imageRepository.listByProductIds(publishedIds));
                images.sort(Comparator.comparing(ProductImage::getSort,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductImage::getId));
                for (ProductImage img : images) {
                    if (img.getKind() == ImageKind.GALLERY) {
                        mainImages.putIfAbsent(img.getProductId(), img.getUrl());
                    }
                }
                // 保持入参顺序装配；缺失/下架静默剔除（CV-MKT-006）
                Map<Long, Product> byId = new HashMap<>();
                for (Product p : products) {
                    if (p.getStatus() == ProductStatus.PUBLISHED) {
                        byId.put(p.getId(), p);
                    }
                }
                List<ProductRef> refs = new ArrayList<>();
                for (Long id : productIds) {
                    Product p = byId.get(id);
                    if (p == null) {
                        continue;
                    }
                    refs.add(new ProductRef(p.getId(), p.getSlug(),
                            translatedNames.getOrDefault(p.getId(), p.getName()),
                            p.getPrice(), mainImages.get(p.getId())));
                }
                return refs;
            }

            @Override
            public List<Long> listExistingIds(Collection<Long> productIds) {
                if (productIds == null || productIds.isEmpty()) {
                    return List.of();
                }
                return productRepository.listByIds(productIds).stream().map(Product::getId).toList();
            }
        };
    }
}
