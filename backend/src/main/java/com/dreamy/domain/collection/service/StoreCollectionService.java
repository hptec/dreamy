package com.dreamy.domain.collection.service;

import com.dreamy.domain.product.repository.ProductCollectionRepository;
import com.dreamy.domain.collection.entity.Collection;
import com.dreamy.domain.collection.entity.CollectionGroup;
import com.dreamy.domain.collection.entity.CollectionGroupTranslation;
import com.dreamy.domain.collection.entity.CollectionTranslation;
import com.dreamy.domain.collection.repository.CollectionGroupRepository;
import com.dreamy.domain.collection.repository.CollectionRepository;
import com.dreamy.dto.StoreCollectionGroup;
import com.dreamy.dto.StoreCollectionGroup.StoreCollectionItem;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端集合导航服务（E-CAT-07 listStoreCollections）。
 * L2 TRACE: E-CAT-07 STEP-CAT-01~05 / CACHE-CAT-006 / MAP-CAT-007。
 */
@Service
public class StoreCollectionService {

    private final CollectionGroupRepository groupRepository;
    private final CollectionRepository collectionRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final CatalogCacheService cache;

    public StoreCollectionService(CollectionGroupRepository groupRepository, CollectionRepository collectionRepository,
                                  ProductCollectionRepository productCollectionRepository, CatalogCacheService cache) {
        this.groupRepository = groupRepository;
        this.collectionRepository = collectionRepository;
        this.productCollectionRepository = productCollectionRepository;
        this.cache = cache;
    }

    /** E-CAT-07：集合导航（status=enabled 按分组分组；product_count published 口径） */
    @SuppressWarnings("unchecked")
    public List<StoreCollectionGroup> listGroups(Long groupId, String locale) {
        // STEP-CAT-01 查 JetCache catalog:collections:{group_id|all}:{locale}（TTL 600s）
        String cacheKey = (groupId == null ? "all" : groupId.toString()) + ":" + locale;
        Object cached = cache.get(Family.COLLECTIONS, cacheKey);
        if (cached instanceof List<?> hit) {
            return (List<StoreCollectionGroup>) hit;
        }
        // STEP-CAT-02 分组（过滤；不存在 → 空 items）+ enabled 集合分组
        List<CollectionGroup> groups;
        if (groupId != null) {
            CollectionGroup group = groupRepository.findById(groupId);
            groups = group == null ? List.of() : List.of(group);
        } else {
            groups = groupRepository.listAll();
        }
        List<Collection> collections = collectionRepository.listEnabled(groupId);
        // STEP-CAT-03 product_count 聚合（published 口径，RM-CAT-145）
        Map<Long, Integer> counts = productCollectionRepository.countByCollections(true);
        // STEP-CAT-04 locale 翻译覆盖，缺翻译回退 EN（决策 13）
        Map<Long, String> groupNames = groupNames(groups, locale);
        Map<Long, String> collectionNames = collectionNames(collections, locale);
        Map<Long, List<Collection>> collectionsByGroup = new HashMap<>();
        for (Collection c : collections) {
            collectionsByGroup.computeIfAbsent(c.getCollectionGroupId(), k -> new ArrayList<>()).add(c);
        }
        List<StoreCollectionGroup> groupsResult = new ArrayList<>();
        for (CollectionGroup group : groups) {
            List<StoreCollectionItem> items = collectionsByGroup.getOrDefault(group.getId(), List.of()).stream()
                    .map(c -> new StoreCollectionItem(c.getId(),
                            collectionNames.getOrDefault(c.getId(), c.getName()),
                            c.getCover(),
                            counts.getOrDefault(c.getId(), 0)))
                    .toList();
            groupsResult.add(new StoreCollectionGroup(group.getId(),
                    groupNames.getOrDefault(group.getId(), group.getName()),
                    group.getDescription(), items));
        }
        // STEP-CAT-05 写 JetCache TTL 600s
        cache.put(Family.COLLECTIONS, cacheKey, new ArrayList<>(groupsResult));
        return groupsResult;
    }

    private Map<Long, String> groupNames(List<CollectionGroup> groups, String locale) {
        Map<Long, String> names = new HashMap<>();
        if ("es".equals(locale) || "fr".equals(locale)) {
            List<Long> ids = groups.stream().map(CollectionGroup::getId).toList();
            for (CollectionGroupTranslation t : groupRepository.listTranslationsByGroupIds(ids)) {
                if (locale.equals(t.getLocale()) && t.getName() != null && !t.getName().isBlank()) {
                    names.put(t.getCollectionGroupId(), t.getName());
                }
            }
        }
        return names;
    }

    private Map<Long, String> collectionNames(List<Collection> collections, String locale) {
        Map<Long, String> names = new HashMap<>();
        if ("es".equals(locale) || "fr".equals(locale)) {
            List<Long> ids = collections.stream().map(Collection::getId).toList();
            for (CollectionTranslation t : collectionRepository.listTranslationsByCollectionIds(ids)) {
                if (locale.equals(t.getLocale()) && t.getLabel() != null && !t.getLabel().isBlank()) {
                    names.put(t.getCollectionId(), t.getLabel());
                }
            }
        }
        return names;
    }
}
