package com.dreamy.domain.category.service;

import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.entity.CategoryTranslation;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.dto.StoreCategoryNode;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端分类树服务（E-CAT-06 listStoreCategories）。
 * L2 TRACE: E-CAT-06 STEP-CAT-01~05 / CACHE-CAT-005 / MAP-CAT-005。
 */
@Service
public class StoreCategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryTreeService treeService;
    private final ProductRepository productRepository;
    private final CatalogCacheService cache;

    public StoreCategoryService(CategoryRepository categoryRepository, CategoryTreeService treeService,
                                ProductRepository productRepository, CatalogCacheService cache) {
        this.categoryRepository = categoryRepository;
        this.treeService = treeService;
        this.productRepository = productRepository;
        this.cache = cache;
    }

    /** E-CAT-06：分类树（三层，product_count published 口径，locale 翻译回退） */
    @SuppressWarnings("unchecked")
    public List<StoreCategoryNode> listTree(String locale) {
        // STEP-CAT-01 查 JetCache catalog:categories:{locale}（TTL 600s）
        String cacheKey = locale;
        CatalogCacheService.Lookup lookup = cache.lookup(Family.CATEGORIES, cacheKey);
        Object cached = lookup.value();
        if (cached instanceof List<?> hit) {
            return (List<StoreCategoryNode>) hit;
        }
        // STEP-CAT-02 全量 → 内存组装三层树
        List<Category> all = categoryRepository.listAll();
        // STEP-CAT-03 product_count published 口径，自底向上累加（RM-CAT-096）
        Map<Long, Integer> counts = treeService.rollupCounts(all, productRepository.countByCategoryPublished());
        // STEP-CAT-04 locale=es/fr → category_translation 批查覆盖 name，缺翻译回退 EN（决策 13）
        Map<Long, String> translated = translatedNames(all, locale);
        List<StoreCategoryNode> tree = buildTree(all, null, counts, translated);
        // STEP-CAT-05 写 JetCache TTL 600s
        cache.put(lookup, tree instanceof Serializable ? tree : new ArrayList<>(tree));
        return tree;
    }

    private Map<Long, String> translatedNames(List<Category> all, String locale) {
        Map<Long, String> names = new HashMap<>();
        if ("es".equals(locale) || "fr".equals(locale)) {
            List<Long> ids = all.stream().map(Category::getId).toList();
            for (CategoryTranslation t : categoryRepository.listTranslationsByCategoryIds(ids)) {
                if (locale.equals(t.getLocale()) && t.getName() != null && !t.getName().isBlank()) {
                    names.put(t.getCategoryId(), t.getName());
                }
            }
        }
        return names;
    }

    private List<StoreCategoryNode> buildTree(List<Category> all, Long parentId,
                                              Map<Long, Integer> counts, Map<Long, String> translated) {
        List<StoreCategoryNode> nodes = new ArrayList<>();
        for (Category c : all) {
            boolean isChild = parentId == null ? c.getParentId() == null : parentId.equals(c.getParentId());
            if (!isChild) {
                continue;
            }
            nodes.add(new StoreCategoryNode(
                    c.getId(),
                    translated.getOrDefault(c.getId(), c.getName()),
                    c.getParentId(),
                    c.getLevel(),
                    c.getSort(),
                    counts.getOrDefault(c.getId(), 0),
                    buildTree(all, c.getId(), counts, translated)));
        }
        nodes.sort(Comparator.comparing(StoreCategoryNode::sort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StoreCategoryNode::id));
        return nodes;
    }
}
