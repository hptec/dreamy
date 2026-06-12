package com.dreamy.domain.tag.service;

import com.dreamy.domain.product.repository.ProductTagRepository;
import com.dreamy.domain.tag.entity.Tag;
import com.dreamy.domain.tag.entity.TagDimension;
import com.dreamy.domain.tag.entity.TagDimensionTranslation;
import com.dreamy.domain.tag.entity.TagTranslation;
import com.dreamy.domain.tag.repository.TagDimensionRepository;
import com.dreamy.domain.tag.repository.TagRepository;
import com.dreamy.dto.StoreTagDimensionGroup;
import com.dreamy.dto.StoreTagDimensionGroup.StoreTagItem;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端标签导航服务（E-CAT-07 listStoreTags）。
 * L2 TRACE: E-CAT-07 STEP-CAT-01~05 / CACHE-CAT-006 / MAP-CAT-007。
 */
@Service
public class StoreTagService {

    private final TagDimensionRepository dimensionRepository;
    private final TagRepository tagRepository;
    private final ProductTagRepository productTagRepository;
    private final CatalogCacheService cache;

    public StoreTagService(TagDimensionRepository dimensionRepository, TagRepository tagRepository,
                           ProductTagRepository productTagRepository, CatalogCacheService cache) {
        this.dimensionRepository = dimensionRepository;
        this.tagRepository = tagRepository;
        this.productTagRepository = productTagRepository;
        this.cache = cache;
    }

    /** E-CAT-07：标签导航（status=enabled 按维度分组；product_count published 口径） */
    @SuppressWarnings("unchecked")
    public List<StoreTagDimensionGroup> listGroups(Long dimensionId, String locale) {
        // STEP-CAT-01 查 JetCache catalog:tags:{dimension_id|all}:{locale}（TTL 600s）
        String cacheKey = (dimensionId == null ? "all" : dimensionId.toString()) + ":" + locale;
        Object cached = cache.get(Family.TAGS, cacheKey);
        if (cached instanceof List<?> hit) {
            return (List<StoreTagDimensionGroup>) hit;
        }
        // STEP-CAT-02 维度（过滤；不存在 → 空 items）+ enabled 标签分组
        List<TagDimension> dims;
        if (dimensionId != null) {
            TagDimension dim = dimensionRepository.findById(dimensionId);
            dims = dim == null ? List.of() : List.of(dim);
        } else {
            dims = dimensionRepository.listAll();
        }
        List<Tag> tags = tagRepository.listEnabled(dimensionId);
        // STEP-CAT-03 product_count 聚合（published 口径，RM-CAT-145）
        Map<Long, Integer> counts = productTagRepository.countByTags(true);
        // STEP-CAT-04 locale 翻译覆盖，缺翻译回退 EN（决策 13）
        Map<Long, String> dimNames = dimensionNames(dims, locale);
        Map<Long, String> tagNames = tagNames(tags, locale);
        Map<Long, List<Tag>> tagsByDim = new HashMap<>();
        for (Tag tag : tags) {
            tagsByDim.computeIfAbsent(tag.getDimensionId(), k -> new ArrayList<>()).add(tag);
        }
        List<StoreTagDimensionGroup> groups = new ArrayList<>();
        for (TagDimension dim : dims) {
            List<StoreTagItem> items = tagsByDim.getOrDefault(dim.getId(), List.of()).stream()
                    .map(t -> new StoreTagItem(t.getId(),
                            tagNames.getOrDefault(t.getId(), t.getName()),
                            t.getCover(),
                            counts.getOrDefault(t.getId(), 0)))
                    .toList();
            groups.add(new StoreTagDimensionGroup(dim.getId(),
                    dimNames.getOrDefault(dim.getId(), dim.getName()),
                    dim.getDescription(), items));
        }
        // STEP-CAT-05 写 JetCache TTL 600s
        cache.put(Family.TAGS, cacheKey, new ArrayList<>(groups));
        return groups;
    }

    private Map<Long, String> dimensionNames(List<TagDimension> dims, String locale) {
        Map<Long, String> names = new HashMap<>();
        if ("es".equals(locale) || "fr".equals(locale)) {
            List<Long> ids = dims.stream().map(TagDimension::getId).toList();
            for (TagDimensionTranslation t : dimensionRepository.listTranslationsByDimensionIds(ids)) {
                if (locale.equals(t.getLocale()) && t.getName() != null && !t.getName().isBlank()) {
                    names.put(t.getTagDimensionId(), t.getName());
                }
            }
        }
        return names;
    }

    private Map<Long, String> tagNames(List<Tag> tags, String locale) {
        Map<Long, String> names = new HashMap<>();
        if ("es".equals(locale) || "fr".equals(locale)) {
            List<Long> ids = tags.stream().map(Tag::getId).toList();
            for (TagTranslation t : tagRepository.listTranslationsByTagIds(ids)) {
                if (locale.equals(t.getLocale()) && t.getLabel() != null && !t.getLabel().isBlank()) {
                    names.put(t.getTagId(), t.getLabel());
                }
            }
        }
        return names;
    }
}
