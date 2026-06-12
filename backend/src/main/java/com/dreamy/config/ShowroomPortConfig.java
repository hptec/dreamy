package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.enums.ImageKind;
import com.dreamy.enums.ProductStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.ProductImage;
import com.dreamy.domain.product.entity.ProductTranslation;
import com.dreamy.domain.product.repository.ProductImageMapper;
import com.dreamy.domain.product.repository.ProductMapper;
import com.dreamy.domain.product.repository.ProductTranslationMapper;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.user.repository.UserMapper;
import com.dreamy.port.ShowroomCatalogSnapshotPort;
import com.dreamy.port.ShowroomIdentityQueryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * showroom 域跨域端口装配（决策 3：进程内直调防腐层，禁止跨域直查表语义在端口实现内收口）。
 * - ShowroomCatalogSnapshotPort：基于 catalog 既有 Mapper 的只读适配（name 按 locale 经 product_translation
 *   回退解析——决策 13；image_url 取 gallery sort 最小为主图，CV-CAT-010 口径；
 *   提供方后续给出真实 bean 自动让位，review/marketing 同范式）。
 * - ShowroomIdentityQueryPort：基于 identity UserMapper 的只读适配。
 * L2 TRACE: SHR-IMPL-PORT / showroom-data-detail §8.4。
 */
@Configuration
public class ShowroomPortConfig {

    @Bean
    @ConditionalOnMissingBean(ShowroomCatalogSnapshotPort.class)
    public ShowroomCatalogSnapshotPort showroomCatalogSnapshotPortAdapter(ProductMapper productMapper,
                                                                  ProductTranslationMapper translationMapper,
                                                                  ProductImageMapper imageMapper) {
        return (productIds, locale) -> {
            Map<Long, ShowroomCatalogSnapshotPort.ProductCardBrief> result = new LinkedHashMap<>();
            if (productIds == null || productIds.isEmpty()) {
                return result;
            }
            Map<Long, String> translatedNames = resolveTranslatedNames(translationMapper, productIds, locale);
            Map<Long, String> primaryImages = resolvePrimaryImages(imageMapper, productIds);
            for (Product product : productMapper.selectList(
                    new LambdaQueryWrapper<Product>().in(Product::getId, productIds))) {
                String name = translatedNames.getOrDefault(product.getId(), product.getName());
                result.put(product.getId(), new ShowroomCatalogSnapshotPort.ProductCardBrief(
                        product.getId(), product.getSlug(), name, product.getPrice(),
                        primaryImages.get(product.getId()), product.getCustomSizeAvailable(),
                        product.getLeadTimeDays(), product.getStatus() == ProductStatus.PUBLISHED));
            }
            return result;
        };
    }

    /** name 按 locale 回退解析（en 基准列；es/fr 命中翻译行且非空才覆盖——决策 13 翻译回退） */
    private Map<Long, String> resolveTranslatedNames(ProductTranslationMapper translationMapper,
                                                     Collection<Long> productIds, String locale) {
        Map<Long, String> names = new HashMap<>();
        if (locale == null || "en".equals(locale)) {
            return names;
        }
        for (ProductTranslation t : translationMapper.selectList(new LambdaQueryWrapper<ProductTranslation>()
                .in(ProductTranslation::getProductId, productIds)
                .eq(ProductTranslation::getLocale, locale))) {
            if (t.getName() != null && !t.getName().isBlank()) {
                names.put(t.getProductId(), t.getName());
            }
        }
        return names;
    }

    /** 主图：kind=gallery 按 sort 升序首张（CV-CAT-010 口径），单次 IN 批查防 N+1 */
    private Map<Long, String> resolvePrimaryImages(ProductImageMapper imageMapper, Collection<Long> productIds) {
        Map<Long, String> images = new HashMap<>();
        for (ProductImage image : imageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                .in(ProductImage::getProductId, productIds)
                .eq(ProductImage::getKind, ImageKind.GALLERY)
                .orderByAsc(ProductImage::getSort)
                .orderByAsc(ProductImage::getId))) {
            images.putIfAbsent(image.getProductId(), image.getUrl());
        }
        return images;
    }

    @Bean
    @ConditionalOnMissingBean(ShowroomIdentityQueryPort.class)
    public ShowroomIdentityQueryPort showroomIdentityQueryPortAdapter(UserMapper userMapper) {
        return customerId -> {
            if (customerId == null) {
                return null;
            }
            User user = userMapper.selectById(customerId);
            return user == null ? null : user.getName();
        };
    }
}
