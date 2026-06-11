package com.dreamy.catalog.domain.category.service;

import com.dreamy.catalog.domain.attribute.entity.AttributeSet;
import com.dreamy.catalog.domain.attribute.entity.AttributeSetItem;
import com.dreamy.catalog.domain.attribute.entity.AttributeDef;
import com.dreamy.catalog.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.catalog.domain.attribute.repository.AttributeSetRepository;
import com.dreamy.catalog.domain.category.entity.Category;
import com.dreamy.catalog.domain.category.repository.CategoryRepository;
import com.dreamy.catalog.domain.enums.AttributeVisibility;
import com.dreamy.catalog.domain.enums.AttributeType;
import com.dreamy.catalog.domain.product.repository.ProductRepository;
import com.dreamy.catalog.dto.AdminCategoryUpsert;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.catalog.event.ContentInvalidatedPublisher;
import com.dreamy.catalog.infra.AfterCommitRunner;
import com.dreamy.catalog.infra.CatalogAuditRecorder;
import com.dreamy.catalog.infra.CatalogCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分类层级/属性 delta/删除守卫单元测试（category_lifecycle TASK-034）。
 * L2 TRACE: TC-CAT-007（attr_overrides 合并校验）/ TC-CAT-008（层级）/ TC-CAT-036·057（守卫单测面）
 * / V-CAT-043~048 / CV-CAT-008。
 */
@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {

    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    AttributeSetRepository attributeSetRepository;
    @Mock
    AttributeDefRepository attributeDefRepository;
    @Mock
    CatalogCacheService cache;
    @Mock
    CatalogAuditRecorder audit;
    @Mock
    AfterCommitRunner afterCommit;
    @Mock
    ContentInvalidatedPublisher invalidatedPublisher;

    AdminCategoryService service;
    CategoryTreeService treeService;

    @BeforeEach
    void setUp() {
        treeService = new CategoryTreeService(categoryRepository);
        service = new AdminCategoryService(categoryRepository, treeService, productRepository,
                attributeSetRepository, attributeDefRepository, cache, audit, afterCommit,
                invalidatedPublisher);
    }

    private static Category category(long id, Long parentId, int level, Long attributeSetId) {
        Category c = new Category();
        c.setId(id);
        c.setName("C" + id);
        c.setParentId(parentId);
        c.setLevel(level);
        c.setAttributeSetId(attributeSetId);
        c.setSort(0);
        return c;
    }

    @Test
    @DisplayName("TC-CAT-008 [P0]: 层级计算——根=1、子=parent+1；三层下再建子 → 409505")
    void levelComputation() {
        assertThat(treeService.computeLevel(null)).isEqualTo(1);
        assertThat(treeService.computeLevel(category(1, null, 1, 1L))).isEqualTo(2);
        // 父为第 3 层 → 建子超限 409505
        Category level3 = category(3, 2L, 3, null);
        when(categoryRepository.findById(3L)).thenReturn(level3);
        AdminCategoryUpsert req = new AdminCategoryUpsert("Sub", 3L, null, null, null, null);
        assertThatThrownBy(() -> service.create(req))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.CATEGORY_LEVEL_EXCEEDED));
    }

    @Test
    @DisplayName("V-CAT-044 [P0]: 根分类缺 attribute_set_id → 422501 required_for_root；属性集不存在 → 404503")
    void rootCategoryRules() {
        assertThatThrownBy(() -> service.create(new AdminCategoryUpsert("Root", null, null, null, null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attribute_set_id", "required_for_root"));
        when(attributeSetRepository.findById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(new AdminCategoryUpsert("Root", null, 9L, null, null, null)))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.ATTRIBUTE_SET_NOT_FOUND));
        // 根分类提交 attr_overrides → root_not_allowed
        when(attributeSetRepository.findById(1L)).thenReturn(new AttributeSet());
        assertThatThrownBy(() -> service.create(new AdminCategoryUpsert("Root", null, 1L,
                Map.of("silhouette", "hidden"), null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attr_overrides", "root_not_allowed"));
    }

    @Test
    @DisplayName("TC-CAT-007 [P1]: attr_overrides——key 不属于生效属性集拒绝；value 枚举外拒绝；合法 delta 通过")
    void attrOverridesValidation() {
        Category root = category(1, null, 1, 100L);
        when(categoryRepository.findById(1L)).thenReturn(root);
        when(categoryRepository.listAll()).thenReturn(List.of(root));
        AttributeSetItem item = new AttributeSetItem();
        item.setAttributeSetId(100L);
        item.setAttributeId(7L);
        item.setVisibility(AttributeVisibility.VISIBLE);
        when(attributeSetRepository.listItemsBySetIds(List.of(100L))).thenReturn(List.of(item));
        AttributeDef def = new AttributeDef();
        def.setId(7L);
        def.setKey("silhouette");
        def.setType(AttributeType.SELECT);
        when(attributeDefRepository.listByIds(anyCollection())).thenReturn(List.of(def));
        // key 不在生效集
        assertThatThrownBy(() -> service.create(new AdminCategoryUpsert("Sub", 1L, null,
                Map.of("unknown_key", "hidden"), null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attr_overrides", "key_not_in_effective_set"));
        // value 枚举外
        assertThatThrownBy(() -> service.create(new AdminCategoryUpsert("Sub", 1L, null,
                Map.of("silhouette", "mandatory"), null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("attr_overrides", "invalid_enum"));
        // 合法 delta（沿父链继承属性集）→ 通过并落库
        when(categoryRepository.maxSortOfSiblings(1L)).thenReturn(0);
        service.create(new AdminCategoryUpsert("Sub", 1L, null, Map.of("silhouette", "hidden"), null, null));
        verify(categoryRepository).insert(org.mockito.ArgumentMatchers.any(Category.class));
    }

    @Test
    @DisplayName("TC-CAT-036/057（单测面）[P0]: 子树有商品 → 409502（product_count）；有子分类 → 409502（has_children）")
    void deleteGuards() {
        Category root = category(1, null, 1, 100L);
        Category child = category(2, 1L, 2, null);
        when(categoryRepository.findById(1L)).thenReturn(root);
        when(categoryRepository.listAll()).thenReturn(List.of(root, child));
        // 子树（含 child）挂 3 个商品
        when(productRepository.countByCategoryAll()).thenReturn(Map.of(2L, 3));
        assertThatThrownBy(() -> service.delete(1L))
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_HAS_PRODUCTS);
                    assertThat(ce.getDetails()).containsEntry("product_count", 3);
                });
        // 无商品但有子分类 → has_children
        when(productRepository.countByCategoryAll()).thenReturn(Map.of());
        when(categoryRepository.countByParentId(1L)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(1L))
                .satisfies(ex -> assertThat(((CatalogException) ex).getDetails())
                        .containsEntry("reason", "has_children"));
        verify(categoryRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("V-CAT-048 [P0]: parent_id 不可变更 → 422501 immutable（原型无移动节点交互）")
    void parentImmutable() {
        Category existing = category(2, 1L, 2, null);
        when(categoryRepository.findById(2L)).thenReturn(existing);
        assertThatThrownBy(() -> service.update(2L, new AdminCategoryUpsert("Renamed", 9L, null, null, null, null)))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("parent_id", "immutable"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fields(Throwable ex) {
        return (Map<String, String>) ((CatalogException) ex).getDetails().get("fields");
    }
}
