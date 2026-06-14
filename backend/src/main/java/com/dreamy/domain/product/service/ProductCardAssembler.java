package com.dreamy.domain.product.service;

import com.dreamy.enums.ImageKind;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.ProductImage;
import com.dreamy.domain.product.entity.ProductTranslation;
import com.dreamy.domain.product.repository.ProductImageRepository;
import com.dreamy.domain.product.repository.ProductTranslationRepository;
import com.dreamy.dto.StoreProductCard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端商品卡片批量装配器（MAP-CAT-001；NP-CAT-001 防 N+1：image/swatch/translation 一律 IN 批查）。
 * 翻译合并：locale=es/fr 命中字段覆盖 name/sellingPoints，缺翻译逐字段回退 EN（决策 13——TC-CAT-013）。
 */
@Service
public class ProductCardAssembler {

    private final ProductImageRepository imageRepository;
    private final ProductTranslationRepository translationRepository;

    public ProductCardAssembler(ProductImageRepository imageRepository,
                                ProductTranslationRepository translationRepository) {
        this.imageRepository = imageRepository;
        this.translationRepository = translationRepository;
    }

    /** 批量装配（保持入参顺序） */
    public List<StoreProductCard> assemble(List<Product> products, String locale) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        // RM-CAT-111 主图/swatch 批查
        Map<Long, List<ProductImage>> imagesByProduct = new HashMap<>();
        for (ProductImage image : imageRepository.listByProductIds(ids)) {
            imagesByProduct.computeIfAbsent(image.getProductId(), k -> new ArrayList<>()).add(image);
        }
        // RM-CAT-100 翻译批查（仅 es/fr）
        Map<Long, ProductTranslation> translationByProduct = translationsFor(ids, locale);
        List<StoreProductCard> cards = new ArrayList<>(products.size());
        for (Product product : products) {
            cards.add(toCard(product, imagesByProduct.getOrDefault(product.getId(), List.of()),
                    translationByProduct.get(product.getId())));
        }
        return cards;
    }

    /** 翻译表批查（locale ∈ {es,fr} 才查附表） */
    public Map<Long, ProductTranslation> translationsFor(List<Long> productIds, String locale) {
        Map<Long, ProductTranslation> result = new HashMap<>();
        if ("es".equals(locale) || "fr".equals(locale)) {
            for (ProductTranslation t : translationRepository.listByProductIds(productIds, locale)) {
                result.put(t.getProductId(), t);
            }
        }
        return result;
    }

    /** MAP-CAT-001 单卡映射（主图=kind=gallery sort=0；swatches=kind=swatch；rating 冗余列直读） */
    public StoreProductCard toCard(Product product, List<ProductImage> images, ProductTranslation translation) {
        String imageUrl = images.stream()
                .filter(i -> i.getKind() == ImageKind.GALLERY)
                .filter(i -> i.getSort() != null && i.getSort() == 0)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElseGet(() -> images.stream()
                        .filter(i -> i.getKind() == ImageKind.GALLERY)
                        .map(ProductImage::getUrl)
                        .findFirst().orElse(null));
        List<StoreProductCard.Swatch> swatches = images.stream()
                .filter(i -> i.getKind() == ImageKind.SWATCH)
                .map(i -> new StoreProductCard.Swatch(i.getColorName(), i.getUrl()))
                .toList();
        return new StoreProductCard(
                product.getId(),
                product.getSlug(),
                pick(translation == null ? null : translation.getName(), product.getName()),
                product.getPrice(),
                product.getCompareAt(),
                product.getMultiCurrencyPrices(),
                product.getInstallment(),
                product.getIsNew(),
                product.getIsBest(),
                imageUrl,
                swatches,
                product.getRatingAvg(),
                product.getRatingCount(),
                pickList(translation == null ? null : translation.getSellingPoints(), product.getSellingPoints()));
    }

    /** 决策 13 逐字段回退：译文非空白用译文，否则 EN 主表 */
    public static String pick(String translated, String fallback) {
        return translated != null && !translated.isBlank() ? translated : fallback;
    }

    /** List 版本回退：译文非空用译文，否则主表 */
    public static List<String> pickList(List<String> translated, List<String> fallback) {
        return (translated != null && !translated.isEmpty()) ? translated : fallback;
    }
}
