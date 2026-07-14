package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.banner.service.StoreBannerService;
import com.dreamy.domain.category.service.StoreCategoryService;
import com.dreamy.domain.product.service.RecommendationService;
import com.dreamy.domain.product.service.StoreProductService;
import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.repository.AnnouncementRepository;
import com.dreamy.domain.site_builder.repository.FooterRepository;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.domain.site_builder.repository.NavigationItemRepository;
import com.dreamy.domain.wedding.service.StoreWeddingService;
import com.dreamy.dto.StoreCategoryNode;
import com.dreamy.dto.StoreMarketingDtos.StoreBanner;
import com.dreamy.dto.StoreMarketingDtos.StoreRealWedding;
import com.dreamy.dto.StoreProductCard;
import com.dreamy.enums.BannerPosition;
import com.dreamy.support.MarketingPaginatedSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import huihao.page.Paginated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreContentService 首页区块配置聚合")
class StoreContentSectionConfigTest {

    @Mock private HomePageSectionRepository sectionRepository;
    @Mock private NavigationItemRepository navigationRepository;
    @Mock private FooterRepository footerRepository;
    @Mock private AnnouncementRepository announcementRepository;
    @Mock private StoreBannerService bannerService;
    @Mock private StoreCategoryService categoryService;
    @Mock private StoreProductService productService;
    @Mock private RecommendationService recommendationService;
    @Mock private StoreWeddingService weddingService;

    private StoreContentService service;

    @BeforeEach
    void setUp() {
        service = new StoreContentService(sectionRepository, navigationRepository,
                footerRepository, announcementRepository, new ObjectMapper(), bannerService,
                categoryService, productService, recommendationService, weddingService);
    }

    @Test
    void heroUsesAllSharedHeroBannersInSortOrderAndIgnoresSectionData() {
        HomePageSection section = section("hero", "{\"banner_id\":999}");
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(section));
        when(bannerService.list(BannerPosition.HERO, "fr")).thenReturn(List.of(
                new StoreBanner(7L, "Summer", "/hero.jpg", BannerPosition.HERO.getKey(), 0,
                        "Titre", "Sous-titre", "Acheter", "/shop", "Voir", "/look"),
                new StoreBanner(8L, "Autumn", "/hero-2.jpg", BannerPosition.HERO.getKey(), 1,
                        "Deuxième", "Suite", "Découvrir", "/new", null, null)));

        Map<String, Object> data = firstSectionData("fr");
        List<Map<String, Object>> banners = list(data, "banners");

        assertThat(data)
                .containsEntry("title", "Titre")
                .containsEntry("image_url", "/hero.jpg")
                .doesNotContainKey("banner_id");
        assertThat(banners).extracting(item -> item.get("id")).containsExactly(7L, 8L);
        assertThat(banners).extracting(item -> item.get("image_url"))
                .containsExactly("/hero.jpg", "/hero-2.jpg");
        verify(bannerService).list(BannerPosition.HERO, "fr");
    }

    @Test
    void heroIsOmittedWhenNoPublishedBannerIsActive() {
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(section("hero", null)));
        when(bannerService.list(BannerPosition.HERO, "en")).thenReturn(List.of());

        assertThat(service.getHome("en").getSections()).isEmpty();
        verify(bannerService).list(BannerPosition.HERO, "en");
    }

    @Test
    void omittedHeroDoesNotRemoveFollowingSections() {
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(
                section("hero", null),
                section("custom", "{\"heading\":\"Still visible\"}")));
        when(bannerService.list(BannerPosition.HERO, "en")).thenReturn(List.of());

        assertThat(service.getHome("en").getSections())
                .extracting(section -> section.getSectionType())
                .containsExactly("custom");
    }

    @Test
    void themeCardsAutoUsesCountAndRootCategoryOrder() {
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(
                section("theme_cards", "{\"mode\":\"auto\",\"count\":\"2\"}")));
        when(categoryService.listTree("en")).thenReturn(List.of(
                category(1L, "One", List.of()),
                category(2L, "Two", List.of()),
                category(3L, "Three", List.of())));

        List<Map<String, Object>> cards = list(firstSectionData("en"), "cards");

        assertThat(cards).extracting(card -> card.get("id")).containsExactly(1L, 2L);
    }

    @Test
    void themeCardsManualPreservesConfiguredOrderAcrossTreeLevels() {
        long largeId = 4_294_967_296L;
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(section("theme_cards",
                "{\"mode\":\"manual\",\"category_ids\":[\"4294967296\",11,4294967296,999]}")));
        when(categoryService.listTree("en")).thenReturn(List.of(
                category(11L, "Root", List.of(category(largeId, "Child", List.of())))));

        List<Map<String, Object>> cards = list(firstSectionData("en"), "cards");

        assertThat(cards).extracting(card -> card.get("id")).containsExactly(largeId, 11L);
    }

    @Test
    void productRailNewArrivalAndBestSellerUseRealRecommendationBlocks() {
        when(sectionRepository.findEnabledOrderBySort())
                .thenReturn(List.of(section("product_rail", "{\"source\":\"new_arrival\",\"limit\":\"4\"}")))
                .thenReturn(List.of(section("product_rail", "{\"source\":\"best_seller\",\"limit\":3}")));
        when(recommendationService.recommend("new_arrivals", null, null, 4, "en")).thenReturn(List.of());
        when(recommendationService.recommend("best_sellers", null, null, 3, "en")).thenReturn(List.of());

        firstSectionData("en");
        firstSectionData("en");

        verify(recommendationService).recommend("new_arrivals", null, null, 4, "en");
        verify(recommendationService).recommend("best_sellers", null, null, 3, "en");
    }

    @Test
    void productRailManualPassesOrderedDistinctIdsAndMapsCards() {
        long largeId = 4_294_967_296L;
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(section("product_rail",
                "{\"source\":\"recommend\",\"limit\":2,\"product_ids\":[\"4294967296\",3,4294967296]}")));
        when(productService.listPublishedCardsByIds(List.of(largeId, 3L), 2, "en"))
                .thenReturn(List.of(product(largeId, "large"), product(3L, "three")));

        List<Map<String, Object>> products = list(firstSectionData("en"), "products");

        assertThat(products).extracting(item -> item.get("id")).containsExactly(largeId, 3L);
        verify(productService).listPublishedCardsByIds(List.of(largeId, 3L), 2, "en");
    }

    @Test
    void productRailCategoryPassesCategoryLimitAndSortToCatalogQuery() {
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(section("product_rail",
                "{\"source\":\"category\",\"category_id\":\"42\",\"limit\":5,\"sort\":\"price_asc\"}")));
        Paginated<StoreProductCard> page = MarketingPaginatedSupport.of(
                List.of(product(8L, "eight")), 1, 1, 5);
        when(productService.listProducts(any(StoreProductService.ListQuery.class))).thenReturn(page);

        List<Map<String, Object>> products = list(firstSectionData("en"), "products");

        ArgumentCaptor<StoreProductService.ListQuery> queryCaptor =
                ArgumentCaptor.forClass(StoreProductService.ListQuery.class);
        verify(productService).listProducts(queryCaptor.capture());
        StoreProductService.ListQuery query = queryCaptor.getValue();
        assertThat(query.categoryId()).isEqualTo(42L);
        assertThat(query.pageSize()).isEqualTo(5);
        assertThat(query.sort()).isEqualTo("price_asc");
        assertThat(products).extracting(item -> item.get("id")).containsExactly(8L);
    }

    @Test
    void productRailCategoryWithoutCategoryIdFailsClosed() {
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(
                section("product_rail", "{\"source\":\"category\",\"limit\":5}")));

        List<Map<String, Object>> products = list(firstSectionData("en"), "products");

        assertThat(products).isEmpty();
        verify(productService, never()).listProducts(any());
    }

    @Test
    void editorialFeatureKeepsWeddingDateDescendingServiceOrder() {
        HomePageSection section = section("editorial_feature", "{\"limit\":\"2\"}");
        section.setI18nJson("{\"en\":{\"heading\":\"Latest weddings\"}}");
        when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(section));
        Paginated<StoreRealWedding> page = MarketingPaginatedSupport.of(List.of(
                wedding(2L, "2026-06-01"),
                wedding(1L, "2026-05-01")), 2, 1, 2);
        when(weddingService.page(1, 2, "en")).thenReturn(page);

        Map<String, Object> data = firstSectionData("en");
        List<Map<String, Object>> stories = list(data, "stories");

        assertThat(data).containsEntry("heading", "Latest weddings");
        assertThat(stories).extracting(item -> item.get("id")).containsExactly(2L, 1L);
        verify(weddingService).page(1, 2, "en");
    }

    private Map<String, Object> firstSectionData(String locale) {
        Object data = service.getHome(locale).getSections().getFirst().getData();
        assertThat(data).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) data;
        return cast;
    }

    private static HomePageSection section(String type, String dataJson) {
        HomePageSection section = new HomePageSection();
        section.setId(1L);
        section.setSectionType(type);
        section.setDataJson(dataJson);
        return section;
    }

    private static StoreCategoryNode category(long id, String name, List<StoreCategoryNode> children) {
        return new StoreCategoryNode(id, name, null, 0, 0, 0, children);
    }

    private static StoreProductCard product(long id, String slug) {
        return new StoreProductCard(id, slug, "Product " + id, new BigDecimal("99.00"),
                null, null, null, false, false, "/" + slug + ".jpg", List.of(), null, null, List.of());
    }

    private static StoreRealWedding wedding(long id, String weddingDate) {
        return new StoreRealWedding(id, "Couple " + id, "Paris", "Classic", weddingDate,
                "/wedding-" + id + ".jpg", 1, "Wedding " + id, null, null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> data, String key) {
        return (List<Map<String, Object>>) data.get(key);
    }
}
