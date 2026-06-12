package com.dreamy.domain.attribute.service;

import com.dreamy.domain.attribute.entity.AttributeDef;
import com.dreamy.domain.attribute.entity.AttributeSetItem;
import com.dreamy.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.domain.attribute.repository.AttributeSetRepository;
import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.domain.category.service.CategoryTreeService;
import com.dreamy.enums.AttributeVisibility;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类 → 生效属性配置解析（与 portal-admin STORE-CAT-A03 resolveAttributeConfig 同义）：
 * 沿祖先链取最近 attribute_set_id 的矩阵为基底（set item id 序），子分类 attr_overrides delta 合并
 * （已有 key 改可见性；新 key 追加尾部）。AdminProduct 校验 / PDP attributes 装配 / PLP filters 共用。
 */
@Service
public class ProductAttributeConfigService {

    /** 生效属性行（def + 三态可见性，保持属性集顺序） */
    public record ResolvedAttr(AttributeDef def, AttributeVisibility visibility) {
    }

    private final CategoryRepository categoryRepository;
    private final CategoryTreeService treeService;
    private final AttributeSetRepository setRepository;
    private final AttributeDefRepository defRepository;

    public ProductAttributeConfigService(CategoryRepository categoryRepository, CategoryTreeService treeService,
                                         AttributeSetRepository setRepository,
                                         AttributeDefRepository defRepository) {
        this.categoryRepository = categoryRepository;
        this.treeService = treeService;
        this.setRepository = setRepository;
        this.defRepository = defRepository;
    }

    /** 生效属性配置（含 hidden 行，调用方按需过滤）；分类不存在/无属性集 → 空列表 */
    public List<ResolvedAttr> effectiveAttrs(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        List<Category> all = categoryRepository.listAll();
        Map<Long, Category> byId = new HashMap<>();
        for (Category c : all) {
            byId.put(c.getId(), c);
        }
        Category category = byId.get(categoryId);
        if (category == null) {
            return List.of();
        }
        Long setId = treeService.effectiveAttributeSetId(category, byId);
        List<AttributeDef> defs = defRepository.listAll();
        Map<Long, AttributeDef> defById = new HashMap<>();
        Map<String, AttributeDef> defByKey = new HashMap<>();
        for (AttributeDef def : defs) {
            defById.put(def.getId(), def);
            defByKey.put(def.getKey(), def);
        }
        // 基底：属性集矩阵（item id 序）
        LinkedHashMap<String, ResolvedAttr> result = new LinkedHashMap<>();
        if (setId != null) {
            for (AttributeSetItem item : setRepository.listItemsBySetIds(List.of(setId))) {
                AttributeDef def = defById.get(item.getAttributeId());
                if (def != null && item.getVisibility() != null) {
                    result.put(def.getKey(), new ResolvedAttr(def, item.getVisibility()));
                }
            }
        }
        // delta：子分类 attr_overrides（根分类不存 overrides——AdminCategoryService 写入约束）
        if (category.getParentId() != null && category.getAttrOverrides() != null) {
            for (Map.Entry<String, String> entry : category.getAttrOverrides().entrySet()) {
                AttributeVisibility visibility = AttributeVisibility.of(entry.getValue());
                AttributeDef def = defByKey.get(entry.getKey());
                if (visibility == null || def == null) {
                    continue;
                }
                result.put(def.getKey(), new ResolvedAttr(def, visibility));
            }
        }
        return new ArrayList<>(result.values());
    }

    /** 非 hidden 生效属性（admin 保存校验白名单 / PDP 展示口径） */
    public List<ResolvedAttr> visibleAttrs(Long categoryId) {
        return effectiveAttrs(categoryId).stream()
                .filter(a -> a.visibility() != AttributeVisibility.HIDDEN)
                .toList();
    }
}
