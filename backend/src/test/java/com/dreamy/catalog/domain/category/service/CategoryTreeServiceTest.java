package com.dreamy.catalog.domain.category.service;

import com.dreamy.catalog.domain.category.entity.Category;
import com.dreamy.catalog.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 分类树纯逻辑单元测试（子树解析/自底向上计数/叶子规则）。
 * L2 TRACE: TC-CAT-030（两口径累加单测面）/ E-CAT-01 STEP-CAT-02 / 决策 29 complete_the_look。
 */
@ExtendWith(MockitoExtension.class)
class CategoryTreeServiceTest {

    @Mock
    CategoryRepository categoryRepository;
    @InjectMocks
    CategoryTreeService service;

    private static Category category(long id, Long parentId, int level) {
        Category c = new Category();
        c.setId(id);
        c.setName("C" + id);
        c.setParentId(parentId);
        c.setLevel(level);
        return c;
    }

    /** 树：1(根) → 2、3；2 → 4 */
    private List<Category> tree() {
        return List.of(category(1, null, 1), category(2, 1L, 2), category(3, 1L, 2), category(4, 2L, 3));
    }

    @Test
    @DisplayName("E-CAT-01 STEP-CAT-02 [P0]: 子树解析含自身；分类不存在 → 空集（空页不 404）")
    void subtreeIds() {
        when(categoryRepository.listAll()).thenReturn(tree());
        assertThat(service.subtreeIds(1L)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(service.subtreeIds(2L)).containsExactlyInAnyOrder(2L, 4L);
        assertThat(service.subtreeIds(99L)).isEmpty();
        assertThat(service.subtreeIds(null)).isNull();
    }

    @Test
    @DisplayName("TC-CAT-030（单测面）[P1]: product_count 自底向上累加到祖先节点正确（NP-CAT-002）")
    void rollupCounts() {
        Map<Long, Integer> totals = service.rollupCounts(tree(), Map.of(4L, 2, 3L, 5));
        assertThat(totals.get(4L)).isEqualTo(2);
        assertThat(totals.get(2L)).isEqualTo(2);
        assertThat(totals.get(3L)).isEqualTo(5);
        assertThat(totals.get(1L)).isEqualTo(7);
    }

    @Test
    @DisplayName("决策 29 ctl [P0]: 同根其他叶子分类解析（排除基准叶子；非叶子节点不入列）")
    void otherLeavesUnderRoot() {
        when(categoryRepository.listAll()).thenReturn(tree());
        // 根 1 下叶子为 3、4；排除基准叶子 4 → 仅 3
        assertThat(service.otherLeafIdsUnderRoot(1L, 4L)).containsExactly(3L);
    }
}
