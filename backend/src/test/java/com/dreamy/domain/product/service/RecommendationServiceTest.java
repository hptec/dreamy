package com.dreamy.domain.product.service;

import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.domain.category.service.CategoryTreeService;
import com.dreamy.enums.ProductStatus;
import com.dreamy.enums.CollectionStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.collection.entity.Collection;
import com.dreamy.domain.collection.repository.CollectionRepository;
import com.dreamy.dto.StoreProductCard;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.CatalogCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐位规则单元测试（决策 29 五规则 + V-CAT-008~011）。
 * STUB_SCOPE: repository_io + cache（CatalogCacheService 为基建边界）。
 * L2 TRACE: TC-CAT-009 / TC-CAT-049（单测面）/ RM-CAT-091~095。
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    CollectionRepository collectionRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    CategoryTreeService treeService;
    @Mock
    ProductCardAssembler cardAssembler;
    @Mock
    CatalogCacheService cache;

    RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(productRepository, collectionRepository, categoryRepository,
                treeService, cardAssembler, cache);
        lenient().when(cache.get(any(), anyString())).thenReturn(null);
        lenient().when(cardAssembler.assemble(anyList(), anyString()))
                .thenAnswer(inv -> ((List<Product>) inv.getArgument(0)).stream()
                        .map(p -> new StoreProductCard(p.getId(), p.getSlug(), p.getName(),
                                p.getPrice(), null, null, null, null, null, null, List.of(), null, null, null))
                        .toList());
    }

    private static Product product(long id, String price, Long categoryId) {
        Product p = new Product();
        p.setId(id);
        p.setSlug("p-" + id);
        p.setName("P" + id);
        p.setPrice(new BigDecimal(price));
        p.setCategoryId(categoryId);
        p.setStatus(ProductStatus.PUBLISHED);
        return p;
    }

    @Test
    @DisplayName("TC-CAT-009 [P0]: new_arrivals → created_at DESC 规则查询")
    void newArrivals() {
        when(productRepository.listRecoNewArrivals(8)).thenReturn(List.of(product(1, "100", 1L)));
        List<StoreProductCard> items = service.recommend("new_arrivals", null, null, null, "en");
        assertThat(items).hasSize(1);
        verify(productRepository).listRecoNewArrivals(8);
    }

    @Test
    @DisplayName("TC-CAT-009 [P0]: best_sellers sales_30d 全 0 冷启动 → 回退 recommend=true ORDER BY sort")
    void bestSellersColdStartFallback() {
        when(productRepository.listRecoBestSellers(8)).thenReturn(List.of());
        when(productRepository.listRecoRecommendFallback(8)).thenReturn(List.of(product(2, "100", 1L)));
        List<StoreProductCard> items = service.recommend("best_sellers", null, null, null, "en");
        assertThat(items).extracting(StoreProductCard::id).containsExactly(2L);
        verify(productRepository).listRecoRecommendFallback(8);
    }

    @Test
    @DisplayName("TC-CAT-009 [P0]: ymal 同品类 ±30% 价格段；不足 limit 放宽仅同品类补足去重")
    void youMayAlsoLikePriceBand() {
        Product base = product(10, "1000", 5L);
        when(productRepository.findById(10L)).thenReturn(base);
        when(productRepository.listRecoSimilar(eq(5L), eq(new BigDecimal("700.00")),
                eq(new BigDecimal("1300.00")), eq(10L), anyInt()))
                .thenReturn(List.of(product(11, "900", 5L)));
        when(productRepository.listRecoSameCategory(eq(5L), eq(10L), anyInt()))
                .thenReturn(List.of(product(11, "900", 5L), product(12, "2000", 5L)));
        List<StoreProductCard> items = service.recommend("you_may_also_like", 10L, null, 2, "en");
        assertThat(items).extracting(StoreProductCard::id).containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("TC-CAT-049（单测面）[P0]: 基准品不存在/未发布 → 空 items 不 404")
    void ymalBaselineMissing() {
        when(productRepository.findById(10L)).thenReturn(null);
        assertThat(service.recommend("you_may_also_like", 10L, null, null, "en")).isEmpty();
        Product draft = product(11, "100", 1L);
        draft.setStatus(ProductStatus.DRAFT);
        when(productRepository.findById(11L)).thenReturn(draft);
        assertThat(service.recommend("you_may_also_like", 11L, null, null, "en")).isEmpty();
    }

    @Test
    @DisplayName("TC-CAT-009 [P0]: shop_by_color collection 不存在/disabled → 空 items 不 404")
    void shopByColorDisabledCollection() {
        when(collectionRepository.findById(7L)).thenReturn(null);
        assertThat(service.recommend("shop_by_color", null, 7L, null, "en")).isEmpty();
        Collection disabled = new Collection();
        disabled.setId(8L);
        disabled.setStatus(CollectionStatus.DISABLED);
        when(collectionRepository.findById(8L)).thenReturn(disabled);
        assertThat(service.recommend("shop_by_color", null, 8L, null, "en")).isEmpty();
        verify(productRepository, never()).listRecoByCollection(any(), anyInt());
    }

    @Test
    @DisplayName("V-CAT-008~011 [P0]: block 必填/枚举外、ymal 缺 product_id、sbc 缺 collection_id、limit 越界 → 422501")
    void paramValidation() {
        assertThatThrownBy(() -> service.recommend(null, null, null, null, "en"))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("block", "required"));
        assertThatThrownBy(() -> service.recommend("hot_picks", null, null, null, "en"))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("block", "invalid_enum"));
        assertThatThrownBy(() -> service.recommend("you_may_also_like", null, null, null, "en"))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("product_id", "required"));
        assertThatThrownBy(() -> service.recommend("complete_the_look", null, null, null, "en"))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("product_id", "required"));
        assertThatThrownBy(() -> service.recommend("shop_by_color", null, null, null, "en"))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("collection_id", "required"));
        assertThatThrownBy(() -> service.recommend("new_arrivals", null, null, 25, "en"))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("limit", "range_invalid"));
        assertThatThrownBy(() -> service.recommend("new_arrivals", null, null, 0, "en"))
                .satisfies(ex -> assertThat(fields(ex)).containsEntry("limit", "range_invalid"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fields(Throwable ex) {
        CatalogException ce = (CatalogException) ex;
        assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED);
        return (Map<String, String>) ce.getDetails().get("fields");
    }
}
