package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.site_builder.entity.NavigationItem;
import com.dreamy.domain.site_builder.repository.NavigationItemRepository;
import com.dreamy.dto.SiteBuilderDtos.NavigationItemUpsert;
import com.dreamy.dto.SiteBuilderDtos.NavigationSaveRequest;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * NavigationService 单元测试（TC-U021~U035）。
 * 覆盖 acceptance s-003~s-004（状态机）+ bs-081~bs-160（边界场景，重点循环依赖检测）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NavigationService 单元测试")
class NavigationServiceTest {

    @Mock
    private NavigationItemRepository repository;
    @Mock
    private SiteBuilderCacheService cacheService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private NavigationService service;

    @BeforeEach
    void setUp() {
        service = new NavigationService(repository, objectMapper, cacheService);
    }

    @Test
    @DisplayName("TC-N001: saveNavigation 循环依赖检测 → 409802")
    void saveNavigation_cycleDetected_throwsCycle() {
        NavigationItemUpsert item1 = new NavigationItemUpsert();
        item1.setId(1L);
        item1.setParentId(2L);
        item1.setLabel("A");
        NavigationItemUpsert item2 = new NavigationItemUpsert();
        item2.setId(2L);
        item2.setParentId(1L);
        item2.setLabel("B");

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item1, item2));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_ITEM_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("TC-N002: saveNavigation link_type=taxonomy 但 taxonomyId 为空 → 404805")
    void saveNavigation_taxonomyWithoutId_throwsTaxonomyNotFound() {
        NavigationItemUpsert item = new NavigationItemUpsert();
        item.setLabel("Category");
        item.setLinkType("taxonomy");
        item.setTaxonomyId(null);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.TAXONOMY_NOT_FOUND));
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
        NavigationItemUpsert item = new NavigationItemUpsert();
        item.setId(1L);
        item.setLabel("Home");
        item.setLinkType("custom");
        item.setUrl("/");
        item.setSortOrder(0);
        item.setEnabled(true);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        when(repository.findAllOrderBySort()).thenReturn(List.of());

        service.save(request);

        verify(repository).deleteByIdsNotIn(List.of(1L));
        verify(repository).updateById(any(NavigationItem.class));
        verify(cacheService).invalidateNavigationFamily();
    }

    @Test
    @DisplayName("TC-N005: saveNavigation 新增项（id 为 null）走 insert 路径")
    void saveNavigation_newItem_insertPath() {
        NavigationItemUpsert item = new NavigationItemUpsert();
        item.setLabel("New Item");
        item.setLinkType("custom");
        item.setUrl("/new");
        item.setSortOrder(0);
        item.setEnabled(true);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        when(repository.findAllOrderBySort()).thenReturn(List.of());

        service.save(request);

        verify(repository).insert(any(NavigationItem.class));
    }

    @Test
    @DisplayName("TC-N006: saveNavigation 3 级循环依赖检测")
    void saveNavigation_threeLevelCycleDetected() {
        NavigationItemUpsert i1 = new NavigationItemUpsert();
        i1.setId(1L);
        i1.setParentId(3L);
        i1.setLabel("A");
        NavigationItemUpsert i2 = new NavigationItemUpsert();
        i2.setId(2L);
        i2.setParentId(1L);
        i2.setLabel("B");
        NavigationItemUpsert i3 = new NavigationItemUpsert();
        i3.setId(3L);
        i3.setParentId(2L);
        i3.setLabel("C");

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
        NavigationItemUpsert item = new NavigationItemUpsert();
        item.setId(1L);
        item.setParentId(1L);  // 自引用
        item.setLabel("Self");
        item.setLinkType("custom");
        item.setUrl("/");

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.NAVIGATION_ITEM_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("TC-N009: saveNavigation 无 parent_id 顶级项合法")
    void saveNavigation_topLevelItem_success() {
        NavigationItemUpsert item = new NavigationItemUpsert();
        item.setLabel("Top");
        item.setLinkType("custom");
        item.setUrl("/top");
        item.setSortOrder(0);
        item.setEnabled(true);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        when(repository.findAllOrderBySort()).thenReturn(List.of());

        service.save(request);
        verify(repository).insert(any(NavigationItem.class));
    }

    @Test
    @DisplayName("TC-N010: saveNavigation link_type=custom 但 url 为空（允许，由前端校验）")
    void saveNavigation_customWithoutUrl_accepted() {
        NavigationItemUpsert item = new NavigationItemUpsert();
        item.setLabel("Custom No URL");
        item.setLinkType("custom");
        item.setSortOrder(0);
        item.setEnabled(true);

        NavigationSaveRequest request = new NavigationSaveRequest();
        request.setItems(List.of(item));

        when(repository.findAllOrderBySort()).thenReturn(List.of());

        service.save(request);
        verify(repository).insert(any());
    }

    private static org.assertj.core.api.ThrowableAssert.ThrowingCallable assertThatThrownBy(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.assertThatThrownBy(callable);
    }

    private static org.assertj.core.api.Assertions assertThat(Object actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
