package com.dreamy.domain.attribute.service;

import com.dreamy.domain.attribute.entity.AttributeSet;
import com.dreamy.domain.attribute.entity.AttributeSetItem;
import com.dreamy.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.domain.attribute.repository.AttributeSetRepository;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.enums.AttributeVisibility;
import com.dreamy.dto.AdminCatalogDtos.AttributeSetDto;
import com.dreamy.dto.AdminCatalogDtos.AttributeSetItemDto;
import com.dreamy.dto.AdminCatalogDtos.AttributeSetUpsert;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.CatalogAfterCommitRunner;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import com.dreamy.support.CatalogFieldErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台属性集服务（E-CAT-19~22；TX-CAT-009~011；TASK-037 attribute_visibility_cycle 整单覆盖承载）。
 * 属性集矩阵自 EAV 化后同时驱动 PDP attributes 展示与 PLP filters（变更需失效 PRODUCTS/PRODUCT 族）。
 * L2 TRACE: V-CAT-049~052 / CV-CAT-006 / MAP-CAT-008。
 */
@Service
public class AttributeSetService {

    private final AttributeSetRepository setRepository;
    private final AttributeDefRepository defRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogAuditRecorder audit;
    private final CatalogCacheService cache;
    private final CatalogAfterCommitRunner afterCommit;

    public AttributeSetService(AttributeSetRepository setRepository, AttributeDefRepository defRepository,
                               CategoryRepository categoryRepository, CatalogAuditRecorder audit,
                               CatalogCacheService cache, CatalogAfterCommitRunner afterCommit) {
        this.setRepository = setRepository;
        this.defRepository = defRepository;
        this.categoryRepository = categoryRepository;
        this.audit = audit;
        this.cache = cache;
        this.afterCommit = afterCommit;
    }

    /** E-CAT-19：列表（含矩阵明细 + category_count 派生——STEP-CAT-01/02） */
    public List<AttributeSetDto> list() {
        List<AttributeSet> sets = setRepository.listAll();
        List<Long> setIds = sets.stream().map(AttributeSet::getId).toList();
        Map<Long, List<AttributeSetItemDto>> itemsBySet = new HashMap<>();
        for (AttributeSetItem item : setRepository.listItemsBySetIds(setIds)) {
            itemsBySet.computeIfAbsent(item.getAttributeSetId(), k -> new ArrayList<>())
                    .add(new AttributeSetItemDto(item.getAttributeId(),
                            item.getVisibility() == null ? null : item.getVisibility().getKey()));
        }
        List<AttributeSetDto> result = new ArrayList<>();
        for (AttributeSet set : sets) {
            int categoryCount = (int) categoryRepository.countByAttributeSetId(set.getId());
            result.add(new AttributeSetDto(set.getId(), set.getLabel(),
                    itemsBySet.getOrDefault(set.getId(), List.of()), categoryCount));
        }
        return result;
    }

    /** E-CAT-20：新增属性集（TX-CAT-009） */
    @Transactional
    public AttributeSetDto create(AttributeSetUpsert req) {
        List<AttributeSetItem> items = validate(req);
        AttributeSet set = new AttributeSet();
        set.setLabel(req.label().trim());
        setRepository.insert(set);
        setRepository.replaceItems(set.getId(), items);
        audit.record("创建属性集", set.getLabel(), null);
        return new AttributeSetDto(set.getId(), set.getLabel(), normalizedItems(req), 0);
    }

    /** E-CAT-21：编辑属性集（TX-CAT-010 矩阵 DELETE+INSERT 全量重写原子） */
    @Transactional
    public AttributeSetDto update(Long id, AttributeSetUpsert req) {
        AttributeSet existing = setRepository.findById(id);
        if (existing == null) {
            // V-CAT-052
            throw new CatalogException(CatalogErrorCode.ATTRIBUTE_SET_NOT_FOUND);
        }
        List<AttributeSetItem> items = validate(req);
        setRepository.updateLabel(id, req.label().trim());
        setRepository.replaceItems(id, items);
        audit.record("编辑属性集", req.label().trim(), null);
        // 矩阵变更影响 PDP attributes/PLP filters（E-CAT-21 STEP-CAT-04 口径更新）
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.PRODUCTS);
            cache.invalidateFamily(Family.PRODUCT);
        });
        int categoryCount = (int) categoryRepository.countByAttributeSetId(id);
        return new AttributeSetDto(id, req.label().trim(), normalizedItems(req), categoryCount);
    }

    /** E-CAT-22：删除属性集（TX-CAT-011；guard 409503 事务内复查） */
    @Transactional
    public void delete(Long id) {
        AttributeSet existing = setRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.ATTRIBUTE_SET_NOT_FOUND);
        }
        long categoryCount = categoryRepository.countByAttributeSetId(id);
        if (categoryCount > 0) {
            throw new CatalogException(CatalogErrorCode.ATTRIBUTE_SET_IN_USE,
                    Map.of("category_count", categoryCount));
        }
        setRepository.replaceItems(id, List.of());
        setRepository.deleteById(id);
        audit.record("删除属性集", existing.getLabel(), null);
    }

    /** V-CAT-049~051 校验并转换 items */
    private List<AttributeSetItem> validate(AttributeSetUpsert req) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        // V-CAT-049 label 必填 trim 非空 ≤64
        if (req.label() == null || req.label().trim().isEmpty()) {
            errors.reject("label", "required");
        } else if (req.label().trim().length() > 64) {
            errors.reject("label", "too_long");
        }
        // V-CAT-050 items 必填（可空数组）；行 attribute_id 存在；visibility 三态
        if (req.items() == null) {
            errors.reject("items", "required");
            errors.throwIfAny();
        }
        List<AttributeSetItem> rows = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Long> attrIds = new ArrayList<>();
        for (AttributeSetItemDto dto : req.items()) {
            if (dto.attributeId() == null) {
                errors.reject("items", "attribute_not_exists");
                continue;
            }
            // V-CAT-051 attribute_id 不重复
            if (!seen.add(dto.attributeId())) {
                errors.reject("items", "duplicated");
                continue;
            }
            AttributeVisibility visibility = AttributeVisibility.of(dto.visibility());
            if (visibility == null) {
                errors.reject("items", "invalid_enum");
                continue;
            }
            attrIds.add(dto.attributeId());
            AttributeSetItem row = new AttributeSetItem();
            row.setAttributeId(dto.attributeId());
            row.setVisibility(visibility);
            rows.add(row);
        }
        if (!attrIds.isEmpty()) {
            Set<Long> existing = new HashSet<>();
            defRepository.listByIds(attrIds).forEach(def -> existing.add(def.getId()));
            for (Long attrId : attrIds) {
                if (!existing.contains(attrId)) {
                    errors.reject("items", "attribute_not_exists");
                    break;
                }
            }
        }
        errors.throwIfAny();
        return rows;
    }

    private List<AttributeSetItemDto> normalizedItems(AttributeSetUpsert req) {
        return req.items() == null ? List.of() : List.copyOf(req.items());
    }
}
