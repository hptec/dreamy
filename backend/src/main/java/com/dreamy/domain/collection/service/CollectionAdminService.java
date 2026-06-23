package com.dreamy.domain.collection.service;

import com.dreamy.enums.CollectionStatus;
import com.dreamy.enums.ImageKind;
import java.time.LocalDateTime;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.ProductCollection;
import com.dreamy.domain.product.entity.ProductImage;
import com.dreamy.domain.product.repository.ProductCollectionRepository;
import com.dreamy.domain.product.repository.ProductImageRepository;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.collection.entity.Collection;
import com.dreamy.domain.collection.entity.CollectionGroup;
import com.dreamy.domain.collection.entity.CollectionGroupTranslation;
import com.dreamy.domain.collection.entity.CollectionTranslation;
import com.dreamy.domain.collection.repository.CollectionGroupRepository;
import com.dreamy.domain.collection.repository.CollectionRepository;
import com.dreamy.dto.AdminCatalogDtos.CollectionGroupDto;
import com.dreamy.dto.AdminCatalogDtos.CollectionGroupUpsert;
import com.dreamy.dto.AdminCatalogDtos.CollectionDto;
import com.dreamy.dto.AdminCatalogDtos.CollectionProductDto;
import com.dreamy.dto.AdminCatalogDtos.CollectionProductsUpsert;
import com.dreamy.dto.AdminCatalogDtos.CollectionUpsert;
import com.dreamy.dto.TranslationDtos.CollectionGroupTranslationDto;
import com.dreamy.dto.TranslationDtos.CollectionTranslationDto;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.event.ContentInvalidatedPublisher;
import com.dreamy.infra.CatalogAfterCommitRunner;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import com.dreamy.support.CatalogFieldErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台集合分组/集合服务（E-CAT-27~34；TX-CAT-015~020；TASK-035/036 lifecycle guard）。
 * L2 TRACE: V-CAT-059~068 / CV-CAT-006 / CACHE-CAT-006 失效链 / MAP-CAT-010。
 */
@Service
public class CollectionAdminService {

    private static final Set<String> TRANSLATION_LOCALES = Set.of("es", "fr");

    private final CollectionGroupRepository groupRepository;
    private final CollectionRepository collectionRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CatalogCacheService cache;
    private final CatalogAuditRecorder audit;
    private final CatalogAfterCommitRunner afterCommit;
    private final ContentInvalidatedPublisher invalidatedPublisher;
    private final ObjectMapper objectMapper;

    public CollectionAdminService(CollectionGroupRepository groupRepository, CollectionRepository collectionRepository,
                                  ProductCollectionRepository productCollectionRepository,
                                  ProductRepository productRepository, ProductImageRepository productImageRepository,
                                  CatalogCacheService cache,
                                  CatalogAuditRecorder audit, CatalogAfterCommitRunner afterCommit,
                                  ContentInvalidatedPublisher invalidatedPublisher, ObjectMapper objectMapper) {
        this.groupRepository = groupRepository;
        this.collectionRepository = collectionRepository;
        this.productCollectionRepository = productCollectionRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.invalidatedPublisher = invalidatedPublisher;
        this.objectMapper = objectMapper;
    }

    // ==================== 集合分组 E-CAT-27~30 ====================

    /** E-CAT-27：分组列表（collection_count 派生 + 三语 translations） */
    public List<CollectionGroupDto> listGroups() {
        List<CollectionGroup> groups = groupRepository.listAll();
        Map<Long, Long> collectionCounts = groupRepository.countCollectionsGroupByGroup();
        Map<Long, List<CollectionGroupTranslationDto>> translations = groupTranslations(
                groups.stream().map(CollectionGroup::getId).toList());
        return groups.stream().map(g -> new CollectionGroupDto(g.getId(), g.getName(), g.getDescription(),
                collectionCounts.getOrDefault(g.getId(), 0L).intValue(),
                translations.getOrDefault(g.getId(), List.of()))).toList();
    }

    /** E-CAT-28：新增分组（TX-CAT-015） */
    @Transactional
    public CollectionGroupDto createGroup(CollectionGroupUpsert req) {
        validateGroup(req);
        CollectionGroup group = new CollectionGroup();
        group.setName(req.name().trim());
        group.setDescription(req.description());
        groupRepository.insert(group);
        groupRepository.replaceTranslations(group.getId(), toGroupTranslationRows(req.translations()));
        audit.record("创建集合分组", group.getName(), null);
        return new CollectionGroupDto(group.getId(), group.getName(), group.getDescription(), 0,
                req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-29：编辑分组（TX-CAT-016） */
    @Transactional
    public CollectionGroupDto updateGroup(Long id, CollectionGroupUpsert req) {
        CollectionGroup existing = groupRepository.findById(id);
        if (existing == null) {
            // V-CAT-061
            throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
        }
        validateGroup(req);
        existing.setName(req.name().trim());
        existing.setDescription(req.description());
        groupRepository.update(existing);
        groupRepository.replaceTranslations(id, toGroupTranslationRows(req.translations()));
        audit.record("编辑集合分组", existing.getName(), null);
        // STEP-CAT-02 提交后失效 catalog:collections:* → MQ collection_changed
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.COLLECTIONS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_COLLECTION_CHANGED);
        });
        int collectionCount = (int) collectionRepository.countByGroupId(id);
        return new CollectionGroupDto(id, existing.getName(), existing.getDescription(), collectionCount,
                req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-30：删除分组（TX-CAT-017；guard 409506——真实端收紧为先清空集合防误删营销数据） */
    @Transactional
    public void deleteGroup(Long id) {
        CollectionGroup existing = groupRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
        }
        long collectionCount = collectionRepository.countByGroupId(id);
        if (collectionCount > 0) {
            throw new CatalogException(CatalogErrorCode.COLLECTION_GROUP_IN_USE, Map.of("collection_count", collectionCount));
        }
        // 逻辑删除：设置 deleted_at = now()
        CollectionGroup patch = new CollectionGroup();
        patch.setId(id);
        patch.setDeletedAt(LocalDateTime.now());
        groupRepository.update(patch);
        audit.record("删除集合分组", existing.getName(), null);
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.COLLECTIONS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_COLLECTION_CHANGED);
        });
    }

    // ==================== 集合 E-CAT-31~34 ====================

    /** E-CAT-31：集合列表（product_count 全量口径 + translations + fallback_cover_urls——V-CAT-062） */
    public List<CollectionDto> listCollections(Long groupId) {
        List<Collection> collections = collectionRepository.listByGroupId(groupId);
        Map<Long, Integer> counts = productCollectionRepository.countByCollections(false);
        Map<Long, List<CollectionTranslationDto>> translations = collectionTranslations(collections.stream().map(Collection::getId).toList());
        // 每集合取前 4 个商品 id（卡片拼图最多 4 格），再批量解析主图
        Map<Long, List<Long>> firstNProductIds = productCollectionRepository.listFirstNProductIdsByCollections(
                collections.stream().map(Collection::getId).toList(), 4);
        Set<Long> allProductIds = new HashSet<>();
        for (List<Long> ids : firstNProductIds.values()) {
            allProductIds.addAll(ids);
        }
        Map<Long, String> primaryImageByProduct = resolvePrimaryImages(allProductIds);
        return collections.stream().map(c -> {
            List<Long> pids = firstNProductIds.getOrDefault(c.getId(), List.of());
            List<String> covers = pids.stream()
                    .map(primaryImageByProduct::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return toCollectionDto(c, counts.getOrDefault(c.getId(), 0), covers,
                    translations.getOrDefault(c.getId(), List.of()));
        }).toList();
    }

    /** 批量解析商品主图（kind=gallery sort 最小；CV-CAT-010 口径，参考 ShowroomPortConfig.resolvePrimaryImages） */
    private Map<Long, String> resolvePrimaryImages(java.util.Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> images = new LinkedHashMap<>();
        for (ProductImage image : productImageRepository.listByProductIds(productIds)) {
            if (image.getKind() == ImageKind.GALLERY) {
                images.putIfAbsent(image.getProductId(), image.getUrl());
            }
        }
        return images;
    }

    /** 解析单个集合的前 N 张商品主图（用于 update/create 返回 DTO 时填充 fallback_cover_urls） */
    private List<String> resolveFallbackCoverUrls(Long collectionId, int n) {
        if (collectionId == null || n <= 0) {
            return List.of();
        }
        List<Long> productIds = productCollectionRepository.listFirstNProductIdsByCollections(
                List.of(collectionId), n).getOrDefault(collectionId, List.of());
        if (productIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> imageByProduct = resolvePrimaryImages(productIds);
        List<String> result = new ArrayList<>();
        for (Long pid : productIds) {
            String url = imageByProduct.get(pid);
            if (url != null) {
                result.add(url);
            }
        }
        return result;
    }

    /** E-CAT-32：新增集合（TX-CAT-018；collection_lifecycle →enabled） */
    @Transactional
    public CollectionDto createCollection(CollectionUpsert req) {
        validateCollection(req);
        Collection collection = new Collection();
        collection.setCollectionGroupId(req.collectionGroupId());
        collection.setName(req.name().trim());
        collection.setStatus(CollectionStatus.of(req.status()));
        collectionRepository.insert(collection);
        collectionRepository.replaceTranslations(collection.getId(), toCollectionTranslationRows(req.translations()));
        audit.record("创建集合", collection.getName(), null);
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.COLLECTIONS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_COLLECTION_CHANGED);
        });
        return toCollectionDto(collection, 0, resolveFallbackCoverUrls(collection.getId(), 4),
                req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-33：编辑集合（TX-CAT-019；Toggle enabled 映射 status；collection_group_id 可改=移动分组） */
    @Transactional
    public CollectionDto updateCollection(Long id, CollectionUpsert req) {
        Collection existing = collectionRepository.findById(id);
        if (existing == null) {
            // V-CAT-067
            throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
        }
        validateCollection(req);
        CollectionStatus oldStatus = existing.getStatus();
        existing.setCollectionGroupId(req.collectionGroupId());
        existing.setName(req.name().trim());
        existing.setStatus(CollectionStatus.of(req.status()));
        collectionRepository.update(existing);
        collectionRepository.replaceTranslations(id, toCollectionTranslationRows(req.translations()));
        // 审计 changes 含 status 流转（BE-DIM-7）
        audit.record("编辑集合", existing.getName(), statusChangesJson(oldStatus, existing.getStatus()));
        // STEP-CAT-02 提交后失效 collections/reco（shop_by_color）/products（collection_id 筛选）→ MQ
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.COLLECTIONS);
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.PRODUCTS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_COLLECTION_CHANGED);
        });
        Map<Long, Integer> counts = productCollectionRepository.countByCollections(false);
        return toCollectionDto(existing, counts.getOrDefault(id, 0), resolveFallbackCoverUrls(id, 4),
                req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-34：删除集合（TX-CAT-020；无前置 guard；product_collection 级联摘除——RM-CAT-144） */
    @Transactional
    public void deleteCollection(Long id) {
        // V-CAT-068：不存在（含非法 id）→ 404505
        Collection existing = collectionRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
        }
        // 逻辑删除：设置 deleted_at = now()
        Collection patch = new Collection();
        patch.setId(id);
        patch.setDeletedAt(LocalDateTime.now());
        collectionRepository.update(patch);
        collectionRepository.deleteTranslationsByCollectionId(id);
        productCollectionRepository.deleteByCollectionId(id);
        audit.record("删除集合", existing.getName(), null);
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.COLLECTIONS);
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.PRODUCTS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_COLLECTION_CHANGED);
        });
    }

    // ==================== 校验/装配 ====================

    /** V-CAT-059/060 */
    private void validateGroup(CollectionGroupUpsert req) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        if (req.name() == null || req.name().trim().isEmpty()) {
            errors.reject("name", "required");
        } else if (req.name().trim().length() > 64) {
            errors.reject("name", "too_long");
        }
        if (req.description() != null && req.description().length() > 255) {
            errors.reject("description", "too_long");
        }
        if (req.translations() != null) {
            Set<String> seen = new HashSet<>();
            for (CollectionGroupTranslationDto t : req.translations()) {
                if (t.locale() == null || !TRANSLATION_LOCALES.contains(t.locale()) || !seen.add(t.locale())) {
                    errors.reject("translations", "invalid_locale");
                    break;
                }
                if (t.name() != null && t.name().length() > 64) {
                    errors.reject("translations", "too_long");
                    break;
                }
            }
        }
        errors.throwIfAny();
    }

    /** V-CAT-063~066 */
    private void validateCollection(CollectionUpsert req) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        // V-CAT-063 collection_group_id 必填且分组存在（不存在 → 404505）
        if (req.collectionGroupId() == null) {
            errors.reject("collection_group_id", "required");
            errors.throwIfAny();
        }
        if (groupRepository.findById(req.collectionGroupId()) == null) {
            throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
        }
        // V-CAT-064 name 必填 trim 非空 ≤64（js_guard 后端兜底）
        if (req.name() == null || req.name().trim().isEmpty()) {
            errors.reject("name", "required");
        } else if (req.name().trim().length() > 64) {
            errors.reject("name", "too_long");
        }
        // V-CAT-065 status 必填枚举
        if (CollectionStatus.of(req.status()) == null) {
            errors.reject("status", "invalid_enum");
        }
        if (req.translations() != null) {
            Set<String> seen = new HashSet<>();
            for (CollectionTranslationDto t : req.translations()) {
                if (t.locale() == null || !TRANSLATION_LOCALES.contains(t.locale()) || !seen.add(t.locale())) {
                    errors.reject("translations", "invalid_locale");
                    break;
                }
                if (t.label() != null && t.label().length() > 64) {
                    errors.reject("translations", "too_long");
                    break;
                }
            }
        }
        errors.throwIfAny();
    }

    private String statusChangesJson(CollectionStatus from, CollectionStatus to) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "before", Map.of("status", from == null ? null : from.getKey()),
                    "after", Map.of("status", to == null ? null : to.getKey())));
        } catch (Exception ex) {
            return null;
        }
    }

    private List<CollectionGroupTranslation> toGroupTranslationRows(List<CollectionGroupTranslationDto> dtos) {
        List<CollectionGroupTranslation> rows = new ArrayList<>();
        if (dtos != null) {
            for (CollectionGroupTranslationDto dto : dtos) {
                CollectionGroupTranslation row = new CollectionGroupTranslation();
                row.setLocale(dto.locale());
                row.setName(dto.name());
                rows.add(row);
            }
        }
        return rows;
    }

    private List<CollectionTranslation> toCollectionTranslationRows(List<CollectionTranslationDto> dtos) {
        List<CollectionTranslation> rows = new ArrayList<>();
        if (dtos != null) {
            for (CollectionTranslationDto dto : dtos) {
                CollectionTranslation row = new CollectionTranslation();
                row.setLocale(dto.locale());
                row.setLabel(dto.label());
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<Long, List<CollectionGroupTranslationDto>> groupTranslations(List<Long> ids) {
        Map<Long, List<CollectionGroupTranslationDto>> result = new HashMap<>();
        for (CollectionGroupTranslation t : groupRepository.listTranslationsByGroupIds(ids)) {
            result.computeIfAbsent(t.getCollectionGroupId(), k -> new ArrayList<>())
                    .add(new CollectionGroupTranslationDto(t.getLocale(), t.getName()));
        }
        return result;
    }

    private Map<Long, List<CollectionTranslationDto>> collectionTranslations(List<Long> collectionIds) {
        Map<Long, List<CollectionTranslationDto>> result = new HashMap<>();
        for (CollectionTranslation t : collectionRepository.listTranslationsByCollectionIds(collectionIds)) {
            result.computeIfAbsent(t.getCollectionId(), k -> new ArrayList<>())
                    .add(new CollectionTranslationDto(t.getLocale(), t.getLabel()));
        }
        return result;
    }

    private CollectionDto toCollectionDto(Collection collection, int productCount, List<String> fallbackCoverUrls,
                                          List<CollectionTranslationDto> translations) {
        return new CollectionDto(collection.getId(), collection.getCollectionGroupId(), collection.getName(),
                collection.getStatus() == null ? null : collection.getStatus().getKey(), productCount, fallbackCoverUrls, translations);
    }

    // ==================== 集合内商品管理 E-CAT-35~37 ====================

    /** E-CAT-35 listCollectionProducts —— 按 sort 升序返回集合内商品 */
    public List<CollectionProductDto> listCollectionProducts(Long collectionId) {
        Collection existing = collectionRepository.findById(collectionId);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
        }
        List<ProductCollection> rows = productCollectionRepository.listByCollectionId(collectionId);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = rows.stream().map(ProductCollection::getProductId).toList();
        Map<Long, Product> productById = new LinkedHashMap<>();
        for (Product p : productRepository.listByIds(productIds)) {
            productById.put(p.getId(), p);
        }
        Map<Long, String> imageByProduct = resolvePrimaryImages(productIds);
        List<CollectionProductDto> items = new ArrayList<>();
        for (ProductCollection pc : rows) {
            Product p = productById.get(pc.getProductId());
            if (p == null) {
                // 商品被逻辑删除但挂载未清理：跳过（与 listByCollectionId 不一致容忍）
                continue;
            }
            items.add(new CollectionProductDto(p.getId(), p.getName(), p.getSlug(),
                    p.getStatus() == null ? null : p.getStatus().getKey(),
                    imageByProduct.get(p.getId()), pc.getSort() == null ? 0 : pc.getSort()));
        }
        return items;
    }

    /** E-CAT-36 replaceCollectionProducts —— 全量覆盖（按入参顺序写 sort，TX-CAT-021） */
    @Transactional
    public void replaceCollectionProducts(Long collectionId, CollectionProductsUpsert req) {
        Collection existing = collectionRepository.findById(collectionId);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
        }
        List<Long> productIds = req == null ? List.of() : req.productIds();
        // 校验 productIds 都存在（V-CAT-069；不存在 → 404501 由 listByIds 静默过滤后比对）
        if (!productIds.isEmpty()) {
            Set<Long> found = new HashSet<>();
            for (Product p : productRepository.listByIds(productIds)) {
                found.add(p.getId());
            }
            for (Long pid : productIds) {
                if (!found.contains(pid)) {
                    throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND,
                            Map.of("product_id", pid));
                }
            }
        }
        productCollectionRepository.replaceAllByCollection(collectionId, productIds);
        audit.record("编辑集合商品", existing.getName(), productsChangeJson(productIds));
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.COLLECTIONS);
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.PRODUCTS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_COLLECTION_CHANGED);
        });
    }

    /** E-CAT-37 removeCollectionProduct —— 单条摘除（TX-CAT-022） */
    @Transactional
    public void removeCollectionProduct(Long collectionId, Long productId) {
        Collection existing = collectionRepository.findById(collectionId);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.COLLECTION_NOT_FOUND);
        }
        productCollectionRepository.deleteByCollectionIdAndProductId(collectionId, productId);
        audit.record("摘除集合商品", existing.getName(),
                productsChangeJson(List.of(productId)));
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.COLLECTIONS);
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.PRODUCTS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_COLLECTION_CHANGED);
        });
    }

    private String productsChangeJson(List<Long> productIds) {
        try {
            return objectMapper.writeValueAsString(Map.of("product_ids", productIds == null ? List.of() : productIds));
        } catch (Exception ex) {
            return null;
        }
    }
}
