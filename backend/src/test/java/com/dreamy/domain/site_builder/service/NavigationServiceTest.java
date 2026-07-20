package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.domain.collection.repository.CollectionRepository;
import com.dreamy.domain.guide.repository.GuideRepository;
import com.dreamy.domain.lookbook.repository.LookbookRepository;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.site_builder.entity.NavigationItem;
import com.dreamy.domain.site_builder.repository.NavigationItemRepository;
import com.dreamy.domain.wedding.repository.RealWeddingRepository;
import com.dreamy.dto.SiteBuilderDtos.NavigationItemUpsert;
import com.dreamy.dto.SiteBuilderDtos.NavigationSaveRequest;
import com.dreamy.enums.LinkType;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NavigationService 单元测试。
 * 覆盖：循环依赖检测 + 按 link_type 校验（custom url / page page_key / 引用型 ref 跨域存在性）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NavigationService 单元测试")
class NavigationServiceTest {

    @Mock
    private NavigationItemRepository repository;
    @Mock
    private com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private BlogPostRepository blogPostRepository;
    @Mock
    private RealWeddingRepository weddingRepository;
    @Mock
    private LookbookRepository lookbookRepository;
    @Mock
    private GuideRepository guideRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private NavigationService service;

    @BeforeEach
    void setUp() {
        service = new NavigationService(repository, objectMapper, cacheTasks,
                categoryRepository, collectionRepository, productRepository,
                blogPostRepository, weddingRepository, lookbookRepository, guideRepository);
    }

    private NavigationItemUpsert customItem(String label, String url) {
        NavigationItemUpsert item = new NavigationItemUpsert();
        item.setLabel(label);
        item.setLinkType(LinkType.CUSTOM.getKey());
        item.setUrl(url);
        item.setSortOrder(0);
        item.setEnabled(true);
        return item;
    }

    @Test
    @DisplayName("TC-N001: saveNavigation 循环依赖检测 → 409802")
    void saveNavigation_cycleDetected_throwsCycle() {
        NavigationItemUpsert item1 = customItem("A", "/a");
        item1.setId(1L);
        item1.setParentId(2L);
        NavigationItemUpsert item2 = customItem("B", "/b");
        item2.setId(2L);
        item2.setParentId(1L);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item1, item2));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_ITEM_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("TC-N002: 引用型 ref_id 为空 → 404805")
    void saveNavigation_refTypeWithoutRefId_throwsRefNotFound() {
        NavigationItemUpsert item = customItem("Category", null);
        item.setLinkType(LinkType.CATEGORY.getKey());
        item.setRefId(null);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_REF_NOT_FOUND));
    }

    @Test
    @DisplayName("TC-N002b: 引用型 ref_id 目标不存在 → 404805")
    void saveNavigation_refTargetMissing_throwsRefNotFound() {
        NavigationItemUpsert item = customItem("Category", null);
        item.setLinkType(LinkType.CATEGORY.getKey());
        item.setRefId(999L);
        when(categoryRepository.findById(999L)).thenReturn(null);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_REF_NOT_FOUND));
    }

    @Test
    @DisplayName("TC-N003: saveNavigation items 为 null → 422808")
    void saveNavigation_nullItems_throwsMismatch() {
        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(null);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class);
    }

    @Test
    @DisplayName("TC-N004: saveNavigation 正常整体替换")
    void saveNavigation_normalReplace_success() {
        NavigationItemUpsert item = customItem("Home", "/");
        item.setId(1L);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        when(repository.findAllOrderBySort()).thenReturn(List.of());

        service.save(request);

        verify(repository).deleteByIdsNotIn(List.of(1L));
        verify(repository).updateById(any(NavigationItem.class));
        verify(cacheTasks).enqueue(anyString(), eq("site_navigation.save"), eq("site_navigation"),
                eq("navigation"), eq("导航配置"), anyList(), isNull(), anyMap(), isNull());
    }

    @Test
    @DisplayName("TC-N005: saveNavigation 新增项（id 为 null）走 insert 路径")
    void saveNavigation_newItem_insertPath() {
        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(customItem("New Item", "/new")));

        when(repository.findAllOrderBySort()).thenReturn(List.of());

        service.save(request);

        verify(repository).insert(any(NavigationItem.class));
    }

    @Test
    @DisplayName("TC-N006: saveNavigation 3 级循环依赖检测")
    void saveNavigation_threeLevelCycleDetected() {
        NavigationItemUpsert i1 = customItem("A", "/a");
        i1.setId(1L);
        i1.setParentId(3L);
        NavigationItemUpsert i2 = customItem("B", "/b");
        i2.setId(2L);
        i2.setParentId(1L);
        NavigationItemUpsert i3 = customItem("C", "/c");
        i3.setId(3L);
        i3.setParentId(2L);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(i1, i2, i3));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_ITEM_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("TC-N007: list 返回全部导航项")
    void list_returnsAll() {
        NavigationItem item = new NavigationItem();
        item.setId(1L);
        item.setLabel("Home");
        when(repository.findAllOrderBySort()).thenReturn(List.of(item));

        service.list();

        verify(repository).findAllOrderBySort();
    }

    @Test
    @DisplayName("TC-N008: saveNavigation 自引用循环（id=parent_id）")
    void saveNavigation_selfReferenceCycle() {
        NavigationItemUpsert item = customItem("Self", "/");
        item.setId(1L);
        item.setParentId(1L);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_ITEM_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("TC-N009: page 型 page_key 非法 → 422810")
    void saveNavigation_invalidPageKey_throws() {
        NavigationItemUpsert item = customItem("Page", null);
        item.setLinkType(LinkType.PAGE.getKey());
        item.setPageKey("not-exist-page");

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_PAGE_KEY_INVALID));
    }

    @Test
    @DisplayName("TC-N010: custom 型 url 为空 → 422809")
    void saveNavigation_customWithoutUrl_throws() {
        NavigationItemUpsert item = customItem("Custom No URL", null);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_URL_REQUIRED));
    }

    @Test
    @DisplayName("TC-N011: page 型合法 page_key 通过，不适用字段被清理")
    void saveNavigation_pageType_cleansIrrelevantFields() {
        NavigationItemUpsert item = customItem("Home", "/should-be-cleared");
        item.setLinkType(LinkType.PAGE.getKey());
        item.setPageKey("home");
        item.setRefId(123L);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        when(repository.findAllOrderBySort()).thenReturn(List.of());

        service.save(request);

        verify(repository).insert(argThat((NavigationItem e) ->
                e.getLinkType() == LinkType.PAGE
                        && "home".equals(e.getPageKey())
                        && e.getUrl() == null
                        && e.getRefId() == null));
    }

    @Test
    @DisplayName("TC-N012: 引用型 category 目标存在 → 通过且 url/pageKey 被清理")
    void saveNavigation_categoryRef_success() {
        Category category = new Category();
        category.setId(7L);
        category.setName("A-Line");
        when(categoryRepository.findById(7L)).thenReturn(category);

        NavigationItemUpsert item = customItem("A-Line", "/should-be-cleared");
        item.setLinkType(LinkType.CATEGORY.getKey());
        item.setRefId(7L);
        item.setPageKey("home");

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        when(repository.findAllOrderBySort()).thenReturn(List.of());

        service.save(request);

        verify(repository).insert(argThat((NavigationItem e) ->
                e.getLinkType() == LinkType.CATEGORY
                        && Long.valueOf(7L).equals(e.getRefId())
                        && e.getUrl() == null
                        && e.getPageKey() == null));
    }
}
