package com.dreamy.domain.product.service;

import com.dreamy.enums.ProductStatus;
import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.domain.category.service.CategoryTreeService;
import com.dreamy.enums.RecommendationBlock;
import com.dreamy.enums.TagStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.tag.entity.Tag;
import com.dreamy.domain.tag.repository.TagRepository;
import com.dreamy.dto.StoreProductCard;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import com.dreamy.support.CatalogFieldErrors;
import com.dreamy.support.StoreParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐位服务（E-CAT-03，决策 29 五规则）。
 * L2 TRACE: V-CAT-008~011 / RM-CAT-091~095 / CACHE-CAT-004。
 */
@Service
public class RecommendationService {

    private final ProductRepository productRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTreeService treeService;
    private final ProductCardAssembler cardAssembler;
    private final CatalogCacheService cache;

    public RecommendationService(ProductRepository productRepository, TagRepository tagRepository,
                                 CategoryRepository categoryRepository, CategoryTreeService treeService,
                                 ProductCardAssembler cardAssembler, CatalogCacheService cache) {
        this.productRepository = productRepository;
        this.tagRepository = tagRepository;
        this.categoryRepository = categoryRepository;
        this.treeService = treeService;
        this.cardAssembler = cardAssembler;
        this.cache = cache;
    }

    /** E-CAT-03：推荐位（block 规则化，一律仅 published） */
    @SuppressWarnings("unchecked")
    public List<StoreProductCard> recommend(String blockParam, Long productId, Long tagId,
                                            Integer limitParam, String localeParam) {
        // V-CAT-008~011 入参校验
        CatalogFieldErrors errors = new CatalogFieldErrors();
        String locale = StoreParams.parseLocale(localeParam, errors);
        RecommendationBlock block = RecommendationBlock.of(blockParam);
        if (blockParam == null || blockParam.isBlank()) {
            errors.reject("block", "required");
        } else if (block == null) {
            errors.reject("block", "invalid_enum");
        }
        if (block != null && block.requiresProductId() && productId == null) {
            errors.reject("product_id", "required");
        }
        if (block != null && block.requiresTagId() && tagId == null) {
            errors.reject("tag_id", "required");
        }
        int limit = 8;
        if (limitParam != null) {
            if (limitParam < 1 || limitParam > 24) {
                errors.reject("limit", "range_invalid");
            } else {
                limit = limitParam;
            }
        }
        errors.throwIfAny();
        // STEP-CAT-01 查缓存 catalog:reco:{block}:{pid|tid|-}:{locale}（TTL 300s）
        String anchor = block.requiresProductId() ? String.valueOf(productId)
                : block.requiresTagId() ? String.valueOf(tagId) : "-";
        String cacheKey = block.getKey() + ":" + anchor + ":" + limit + ":" + locale;
        Object cached = cache.get(Family.RECO, cacheKey);
        if (cached instanceof List<?> hit) {
            return (List<StoreProductCard>) hit;
        }
        // STEP-CAT-02 block 规则查询
        List<Product> products = queryByBlock(block, productId, tagId, limit);
        // STEP-CAT-03 装配卡片 + locale 翻译
        List<StoreProductCard> cards = cardAssembler.assemble(products, locale);
        // STEP-CAT-04 写缓存 TTL 300s
        cache.put(Family.RECO, cacheKey, new ArrayList<>(cards));
        return cards;
    }

    /** 决策 29 五规则（TC-CAT-009 纯函数面） */
    private List<Product> queryByBlock(RecommendationBlock block, Long productId, Long tagId, int limit) {
        return switch (block) {
            case NEW_ARRIVALS -> productRepository.listRecoNewArrivals(limit);
            case BEST_SELLERS -> {
                // sales_30d DESC；全 0 冷启动 → 回退 recommend=true ORDER BY sort（决策 29）
                List<Product> bySales = productRepository.listRecoBestSellers(limit);
                yield bySales.isEmpty() ? productRepository.listRecoRecommendFallback(limit) : bySales;
            }
            case SHOP_BY_COLOR -> {
                // tag 不存在/disabled → 空 items 不 404
                Tag tag = tagRepository.findById(tagId);
                if (tag == null || tag.getStatus() != TagStatus.ENABLED) {
                    yield List.of();
                }
                yield productRepository.listRecoByTag(tagId, limit);
            }
            case YOU_MAY_ALSO_LIKE -> {
                // 基准品不存在或未发布 → 空 items
                Product base = baselinePublished(productId);
                if (base == null) {
                    yield List.of();
                }
                BigDecimal low = base.getPrice().multiply(new BigDecimal("0.7"))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal high = base.getPrice().multiply(new BigDecimal("1.3"))
                        .setScale(2, RoundingMode.HALF_UP);
                List<Product> similar = productRepository.listRecoSimilar(base.getCategoryId(), low, high,
                        base.getId(), limit);
                if (similar.size() < limit) {
                    // 不足放宽为仅同 category_id 补足（去重保持原序）
                    Map<Long, Product> merged = new LinkedHashMap<>();
                    for (Product p : similar) {
                        merged.put(p.getId(), p);
                    }
                    for (Product p : productRepository.listRecoSameCategory(base.getCategoryId(), base.getId(),
                            limit)) {
                        merged.putIfAbsent(p.getId(), p);
                        if (merged.size() >= limit) {
                            break;
                        }
                    }
                    yield new ArrayList<>(merged.values()).subList(0, Math.min(limit, merged.size()));
                }
                yield similar;
            }
            case COMPLETE_THE_LOOK -> {
                // 同根品类下其他叶子分类规则凑数（不建关联表）
                Product base = baselinePublished(productId);
                if (base == null) {
                    yield List.of();
                }
                List<Category> all = categoryRepository.listAll();
                Map<Long, Category> byId = new HashMap<>();
                for (Category c : all) {
                    byId.put(c.getId(), c);
                }
                Category baseCategory = byId.get(base.getCategoryId());
                if (baseCategory == null) {
                    yield List.of();
                }
                Long rootId = treeService.rootIdOf(baseCategory, byId);
                List<Long> otherLeaves = treeService.otherLeafIdsUnderRoot(rootId, base.getCategoryId());
                yield productRepository.listRecoCrossCategory(otherLeaves, base.getId(), limit);
            }
        };
    }

    private Product baselinePublished(Long productId) {
        Product base = productRepository.findById(productId);
        if (base == null || base.getStatus() != ProductStatus.PUBLISHED) {
            return null;
        }
        return base;
    }
}
