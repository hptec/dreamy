package com.dreamy.domain.category.service;

import com.dreamy.domain.attribute.entity.AttributeDef;
import com.dreamy.domain.attribute.entity.AttributeSetItem;
import com.dreamy.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.domain.attribute.repository.AttributeSetRepository;
import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.entity.CategoryTranslation;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.enums.AttributeVisibility;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.dto.AdminCategoryNode;
import com.dreamy.dto.AdminCategoryUpsert;
import com.dreamy.dto.TranslationDtos.CategoryTranslationDto;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.aspect.CatalogAdminWrite;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.support.CatalogFieldErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台分类服务（E-CAT-15~18；TX-CAT-006~008；TASK-034 category_lifecycle guard）。
 * L2 TRACE: V-CAT-043~048 / CV-CAT-005·006·008 / CACHE-CAT-005 失效链。
 */
@Service
public class AdminCategoryService {

    private static final Set<String> TRANSLATION_LOCALES = Set.of("es", "fr");

    private final CategoryRepository categoryRepository;
    private final CategoryTreeService treeService;
    private final ProductRepository productRepository;
    private final AttributeSetRepository attributeSetRepository;
    private final AttributeDefRepository attributeDefRepository;
    private final CatalogAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;

    public AdminCategoryService(CategoryRepository categoryRepository, CategoryTreeService treeService,
                                ProductRepository productRepository, AttributeSetRepository attributeSetRepository,
                                AttributeDefRepository attributeDefRepository, CatalogAuditRecorder audit,
                                CacheInvalidationTaskService cacheTasks) {
        this.categoryRepository = categoryRepository;
        this.treeService = treeService;
        this.productRepository = productRepository;
        this.attributeSetRepository = attributeSetRepository;
        this.attributeDefRepository = attributeDefRepository;
        this.audit = audit;
        this.cacheTasks = cacheTasks;
    }

    /** E-CAT-15：后台三层树（product_count 全量口径含 draft——STEP-CAT-02；translations 原样——STEP-CAT-03） */
    public List<AdminCategoryNode> listTree() {
        List<Category> all = categoryRepository.listAll();
        Map<Long, Integer> counts = treeService.rollupCounts(all, productRepository.countByCategoryAll());
        Map<Long, List<CategoryTranslationDto>> translations = translationsByCategory(all);
        return buildTree(all, null, counts, translations);
    }

    /** E-CAT-16：新增分类（TX-CAT-006） */
    @CatalogAdminWrite
    @Transactional
    public AdminCategoryNode create(AdminCategoryUpsert req) {
        Category parent = validateUpsert(req, null);
        Category category = new Category();
        category.setName(req.name().trim());
        category.setParentId(req.parentId());
        // STEP-CAT-01 level 计算（根=1，否则 parent.level+1）；sort 缺省同层 MAX(sort)+1
        category.setLevel(treeService.computeLevel(parent));
        category.setAttributeSetId(req.attributeSetId());
        category.setAttrOverrides(req.parentId() == null ? null : req.attrOverrides());
        category.setSort(req.sort() != null ? req.sort() : categoryRepository.maxSortOfSiblings(req.parentId()) + 1);
        // STEP-CAT-02 INSERT category + translation
        categoryRepository.insert(category);
        categoryRepository.replaceTranslations(category.getId(), toTranslationRows(req.translations()));
        // STEP-CAT-03 审计（事务内）
        audit.record("创建分类", category.getName(), null);
        enqueue("category.create", category, CacheInvalidationPlans.CATEGORY_CREATE);
        return toNode(category, 0, List.of(), req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-17：编辑分类（TX-CAT-007；V-CAT-048 parent_id 不可变更） */
    @CatalogAdminWrite
    @Transactional
    public AdminCategoryNode update(Long id, AdminCategoryUpsert req) {
        Category existing = categoryRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.CATEGORY_NOT_FOUND);
        }
        // V-CAT-048 parent_id 不可变更（与 DB 不一致 → 422501 fields.parent_id=immutable）
        if (!java.util.Objects.equals(req.parentId(), existing.getParentId())) {
            throw CatalogException.fieldValidation("parent_id", "immutable");
        }
        validateUpsert(req, existing);
        // STEP-CAT-01 UPDATE（attr_overrides delta 整体覆盖）
        existing.setName(req.name().trim());
        existing.setAttributeSetId(req.attributeSetId());
        existing.setAttrOverrides(existing.getParentId() == null ? null : req.attrOverrides());
        if (req.sort() != null) {
            existing.setSort(req.sort());
        }
        categoryRepository.update(existing);
        // STEP-CAT-02 translation 整单覆盖
        categoryRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        // STEP-CAT-03 审计
        audit.record("编辑分类", existing.getName(), null);
        // STEP-CAT-04 提交后失效 catalog:categories:* + catalog:products:* + catalog:product:*
        // （列表含 category_name 派生；attribute_set_id/attr_overrides 变更影响 PDP attributes/PLP filters）→ MQ
        enqueue("category.update", existing, CacheInvalidationPlans.CATEGORY_UPDATE);
        Map<Long, Integer> counts = treeService.rollupCounts(categoryRepository.listAll(),
                productRepository.countByCategoryAll());
        return toNode(existing, counts.getOrDefault(id, 0), List.of(),
                req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-18：删除分类（TX-CAT-008；guard 事务内复查防并发挂商品——TC-CAT-019） */
    @CatalogAdminWrite
    @Transactional
    public void delete(Long id) {
        Category existing = categoryRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.CATEGORY_NOT_FOUND);
        }
        // STEP-CAT-02 guard①：子树（含自身）商品数 > 0 → 409502（details.product_count）
        List<Long> subtree = treeService.subtreeIds(id);
        Map<Long, Integer> counts = productRepository.countByCategoryAll();
        int productCount = subtree.stream().mapToInt(cid -> counts.getOrDefault(cid, 0)).sum();
        if (productCount > 0) {
            throw new CatalogException(CatalogErrorCode.CATEGORY_HAS_PRODUCTS,
                    Map.of("product_count", productCount));
        }
        // STEP-CAT-03 guard②：存在子分类 → 409502（details.reason=has_children，契约口径归并本码）
        if (categoryRepository.countByParentId(id) > 0) {
            throw new CatalogException(CatalogErrorCode.CATEGORY_HAS_PRODUCTS,
                    Map.of("reason", "has_children"));
        }
        // STEP-CAT-04 物理删除 + translation + 审计（先删翻译，避免外键引用主记录）
        categoryRepository.deleteTranslationsByCategoryId(id);
        categoryRepository.deleteById(id);
        audit.record("删除分类", existing.getName(), null);
        enqueue("category.delete", existing, CacheInvalidationPlans.CATEGORY_DELETE);
    }

    private void enqueue(String triggerPoint, Category category,
                         List<com.dreamy.domain.cache.service.CacheInvalidationTarget> targets) {
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, triggerPoint,
                "category", category.getId(), category.getName(), targets, null, Map.of(), null);
    }

    /** V-CAT-043~047 校验；返回父分类（创建场景），编辑场景返回 null */
    private Category validateUpsert(AdminCategoryUpsert req, Category existing) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        // V-CAT-043 name 必填 trim 非空 ≤64
        if (req.name() == null || req.name().trim().isEmpty()) {
            errors.reject("name", "required");
        } else if (req.name().trim().length() > 64) {
            errors.reject("name", "too_long");
        }
        Category parent = null;
        boolean isRoot = req.parentId() == null;
        if (isRoot) {
            // V-CAT-044 根分类 attribute_set_id 必填且存在
            if (req.attributeSetId() == null) {
                errors.reject("attribute_set_id", "required_for_root");
            } else if (attributeSetRepository.findById(req.attributeSetId()) == null) {
                errors.throwIfAny();
                throw new CatalogException(CatalogErrorCode.ATTRIBUTE_SET_NOT_FOUND);
            }
            // V-CAT-047 attr_overrides 仅子分类允许
            if (req.attrOverrides() != null && !req.attrOverrides().isEmpty()) {
                errors.reject("attr_overrides", "root_not_allowed");
            }
        } else {
            // V-CAT-045 父分类存在 → 404502；level ≤ 3 → 409505
            parent = categoryRepository.findById(req.parentId());
            if (existing == null && parent == null) {
                errors.throwIfAny();
                throw new CatalogException(CatalogErrorCode.CATEGORY_NOT_FOUND);
            }
            if (existing == null && treeService.computeLevel(parent) > 3) {
                errors.throwIfAny();
                throw new CatalogException(CatalogErrorCode.CATEGORY_LEVEL_EXCEEDED);
            }
            // 子分类改绑属性集需存在（V-CAT-044 复用：404503）
            if (req.attributeSetId() != null && attributeSetRepository.findById(req.attributeSetId()) == null) {
                errors.throwIfAny();
                throw new CatalogException(CatalogErrorCode.ATTRIBUTE_SET_NOT_FOUND);
            }
            // V-CAT-047 attr_overrides：key 属于生效属性集，value ∈ 三态（CV-CAT-008）
            validateAttrOverrides(req, existing, parent, errors);
        }
        // V-CAT-046 translations locale ∈ {es,fr} 不重复，name ≤64
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return parent;
    }

    private void validateAttrOverrides(AdminCategoryUpsert req, Category existing, Category parent,
                                       CatalogFieldErrors errors) {
        if (req.attrOverrides() == null || req.attrOverrides().isEmpty()) {
            return;
        }
        // 生效属性集：沿祖先链取最近 attribute_set_id（V-CAT-047）
        List<Category> all = categoryRepository.listAll();
        Map<Long, Category> byId = new HashMap<>();
        for (Category c : all) {
            byId.put(c.getId(), c);
        }
        Category context = new Category();
        context.setParentId(req.parentId());
        context.setAttributeSetId(req.attributeSetId());
        Long effectiveSetId = treeService.effectiveAttributeSetId(context, byId);
        Set<String> allowedKeys = new HashSet<>();
        if (effectiveSetId != null) {
            List<AttributeSetItem> items = attributeSetRepository.listItemsBySetIds(List.of(effectiveSetId));
            List<Long> attrIds = items.stream().map(AttributeSetItem::getAttributeId).toList();
            for (AttributeDef def : attributeDefRepository.listByIds(attrIds)) {
                allowedKeys.add(def.getKey());
            }
        }
        for (Map.Entry<String, Integer> entry : req.attrOverrides().entrySet()) {
            if (!allowedKeys.contains(entry.getKey())) {
                errors.reject("attr_overrides", "key_not_in_effective_set");
                break;
            }
            if (AttributeVisibility.of(entry.getValue()) == null) {
                errors.reject("attr_overrides", "invalid_enum");
                break;
            }
        }
    }

    private void validateTranslations(List<CategoryTranslationDto> translations, CatalogFieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (CategoryTranslationDto t : translations) {
            if (t.locale() == null || !TRANSLATION_LOCALES.contains(t.locale()) || !seen.add(t.locale())) {
                errors.reject("translations", "invalid_locale");
                return;
            }
            if (t.name() != null && t.name().length() > 64) {
                errors.reject("translations", "too_long");
                return;
            }
        }
    }

    private List<CategoryTranslation> toTranslationRows(List<CategoryTranslationDto> dtos) {
        List<CategoryTranslation> rows = new ArrayList<>();
        if (dtos != null) {
            for (CategoryTranslationDto dto : dtos) {
                CategoryTranslation row = new CategoryTranslation();
                row.setLocale(dto.locale());
                row.setName(dto.name());
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<Long, List<CategoryTranslationDto>> translationsByCategory(List<Category> all) {
        Map<Long, List<CategoryTranslationDto>> result = new HashMap<>();
        List<Long> ids = all.stream().map(Category::getId).toList();
        for (CategoryTranslation t : categoryRepository.listTranslationsByCategoryIds(ids)) {
            result.computeIfAbsent(t.getCategoryId(), k -> new ArrayList<>())
                    .add(new CategoryTranslationDto(t.getLocale(), t.getName()));
        }
        return result;
    }

    private List<AdminCategoryNode> buildTree(List<Category> all, Long parentId, Map<Long, Integer> counts,
                                              Map<Long, List<CategoryTranslationDto>> translations) {
        List<AdminCategoryNode> nodes = new ArrayList<>();
        for (Category c : all) {
            boolean isChild = parentId == null ? c.getParentId() == null : parentId.equals(c.getParentId());
            if (!isChild) {
                continue;
            }
            nodes.add(new AdminCategoryNode(
                    c.getId(), c.getName(), c.getParentId(), c.getAttributeSetId(), c.getAttrOverrides(),
                    c.getSort(), c.getLevel(), counts.getOrDefault(c.getId(), 0),
                    buildTree(all, c.getId(), counts, translations),
                    translations.getOrDefault(c.getId(), List.of())));
        }
        nodes.sort(Comparator.comparing(AdminCategoryNode::sort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminCategoryNode::id));
        return nodes;
    }

    private AdminCategoryNode toNode(Category c, int productCount, List<AdminCategoryNode> children,
                                     List<CategoryTranslationDto> translations) {
        return new AdminCategoryNode(c.getId(), c.getName(), c.getParentId(), c.getAttributeSetId(),
                c.getAttrOverrides(), c.getSort(), c.getLevel(), productCount, children, translations);
    }
}
