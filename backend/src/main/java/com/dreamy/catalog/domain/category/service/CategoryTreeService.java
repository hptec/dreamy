package com.dreamy.catalog.domain.category.service;

import com.dreamy.catalog.domain.category.entity.Category;
import com.dreamy.catalog.domain.category.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类树纯查询服务（树组装/子树解析/祖先链解析；E-CAT-01 STEP-CAT-02 / E-CAT-03 ctl 规则共用）。
 * L2 TRACE: RM-CAT-001 / V-CAT-047（生效属性集沿祖先链）/ 决策 29 complete_the_look。
 */
@Service
public class CategoryTreeService {

    private final CategoryRepository categoryRepository;

    public CategoryTreeService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 子树 id 集（含自身，最多 3 层）。分类不存在 → 空集（E-CAT-01 STEP-CAT-02：返回空页不 404）。
     */
    public List<Long> subtreeIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        List<Category> all = categoryRepository.listAll();
        Map<Long, List<Category>> byParent = groupByParent(all);
        boolean exists = all.stream().anyMatch(c -> c.getId().equals(categoryId));
        if (!exists) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);
            for (Category child : byParent.getOrDefault(current, List.of())) {
                queue.add(child.getId());
            }
        }
        return result;
    }

    /** 层级计算：根=1，否则 parent.level+1（E-CAT-16 STEP-CAT-01 / V-CAT-045 数据源） */
    public int computeLevel(Category parent) {
        return parent == null ? 1 : (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
    }

    /** 生效属性集：沿祖先链取最近的 attribute_set_id（V-CAT-047 attr_overrides key 校验数据源） */
    public Long effectiveAttributeSetId(Category category, Map<Long, Category> byId) {
        Category cursor = category;
        int hops = 0;
        while (cursor != null && hops++ < 4) {
            if (cursor.getAttributeSetId() != null) {
                return cursor.getAttributeSetId();
            }
            cursor = cursor.getParentId() == null ? null : byId.get(cursor.getParentId());
        }
        return null;
    }

    /** 根分类 id（complete_the_look：同根品类规则，决策 29） */
    public Long rootIdOf(Category category, Map<Long, Category> byId) {
        Category cursor = category;
        int hops = 0;
        while (cursor != null && cursor.getParentId() != null && hops++ < 4) {
            Category parent = byId.get(cursor.getParentId());
            if (parent == null) {
                break;
            }
            cursor = parent;
        }
        return cursor == null ? null : cursor.getId();
    }

    /** 同根下其他叶子分类 id 集（complete_the_look：排除基准品所属叶子，E-CAT-03 STEP-CAT-02） */
    public List<Long> otherLeafIdsUnderRoot(Long rootId, Long exceptLeafId) {
        List<Category> all = categoryRepository.listAll();
        Map<Long, List<Category>> byParent = groupByParent(all);
        // 收集根子树全部节点
        List<Long> subtree = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            subtree.add(current);
            for (Category child : byParent.getOrDefault(current, List.of())) {
                queue.add(child.getId());
            }
        }
        // 叶子=子树内无 children 的节点
        List<Long> leaves = new ArrayList<>();
        for (Long id : subtree) {
            if (byParent.getOrDefault(id, List.of()).isEmpty() && !id.equals(exceptLeafId)) {
                leaves.add(id);
            }
        }
        return leaves;
    }

    /** product_count 自底向上累加到祖先节点（E-CAT-06 STEP-CAT-03 / E-CAT-15 STEP-CAT-02，NP-CAT-002） */
    public Map<Long, Integer> rollupCounts(List<Category> all, Map<Long, Integer> directCounts) {
        Map<Long, Category> byId = new HashMap<>();
        for (Category c : all) {
            byId.put(c.getId(), c);
        }
        Map<Long, Integer> totals = new HashMap<>();
        for (Category c : all) {
            totals.put(c.getId(), directCounts.getOrDefault(c.getId(), 0));
        }
        // 直接计数沿祖先链上卷（最多 3 层）
        for (Map.Entry<Long, Integer> entry : directCounts.entrySet()) {
            Category cursor = byId.get(entry.getKey());
            int hops = 0;
            while (cursor != null && cursor.getParentId() != null && hops++ < 4) {
                Category parent = byId.get(cursor.getParentId());
                if (parent == null) {
                    break;
                }
                totals.merge(parent.getId(), entry.getValue(), Integer::sum);
                cursor = parent;
            }
        }
        return totals;
    }

    public Map<Long, List<Category>> groupByParent(List<Category> all) {
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            if (c.getParentId() != null) {
                byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        return byParent;
    }
}
