package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionDto;
import com.dreamy.dto.SiteBuilderDtos.HomePageSaveItem;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionUpsert;
import com.dreamy.dto.SiteBuilderDtos.SortItem;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HomePageSectionService 单元测试（TC-U001~U020）。
 * 覆盖 acceptance s-001~s-002（状态机）+ bs-001~bs-080（边界场景）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HomePageSectionService 单元测试")
class HomePageSectionServiceTest {

    @Mock
    private HomePageSectionRepository repository;
    @Mock
    private SiteBuilderCacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HomePageSectionService service;

    @BeforeEach
    void setUp() {
        service = new HomePageSectionService(repository, objectMapper, cacheService);
    }

    @Test
    @DisplayName("TC-U001: createHomeSection 正常路径 - product_rail recommend")
    void createHomeSection_productRailRecommend_success() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("product_rail");
        upsert.setEnabled(true);
        upsert.setSortOrder(1);
        upsert.setLabel("New Arrivals");
        JsonNode dataJson = objectMapper.readTree("{\"source\":\"recommend\",\"product_ids\":[1,2,3],\"limit\":6}");
        upsert.setDataJson(dataJson);

        when(repository.insert(any(HomePageSection.class))).thenAnswer(invocation -> {
            HomePageSection entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        HomePageSectionDto result = service.create(upsert);

        assertThat(result).isNotNull();
        assertThat(result.getSectionType()).isEqualTo("product_rail");
        assertThat(result.getEnabled()).isTrue();
        verify(repository).insert(any(HomePageSection.class));
        verify(cacheService).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("Hero 区块为单例，API 不允许重复创建")
    void createSecondHeroIsRejected() {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("hero");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        when(repository.countByTypeExcludingId("hero", null)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getDetails())
                        .containsEntry("reason", "homepage can contain only one hero section"));

        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("TC-U002: createHomeSection js_guard 失败 - hero 但 data_json 非空")
    void createHomeSection_heroWithDataJson_throwsMismatch() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("hero");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        JsonNode dataJson = objectMapper.readTree("{\"foo\":\"bar\"}");
        upsert.setDataJson(dataJson);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> {
                    SiteBuilderException sbEx = (SiteBuilderException) ex;
                    assertThat(sbEx.getErrorCode()).isEqualTo(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH);
                });
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("TC-U003: createHomeSection section_type 非法")
    void createHomeSection_invalidType_throwsInvalid() {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("invalid_type");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_TYPE_INVALID));
    }

    @Test
    @DisplayName("TC-U004: createHomeSection newsletter 缺 i18n_json")
    void createHomeSection_newsletterWithoutI18n_throwsMismatch() {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("newsletter");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH));
    }

    @Test
    @DisplayName("TC-U005: createHomeSection sort_order 负数")
    void createHomeSection_negativeSortOrder_throwsMismatch() {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("custom");
        upsert.setEnabled(true);
        upsert.setSortOrder(-1);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class);
    }

    @Test
    @DisplayName("TC-U006: createHomeSection product_rail source=recommend 但无 product_ids")
    void createHomeSection_productRailRecommendWithoutIds_throwsDataJsonInvalid() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("product_rail");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        JsonNode dataJson = objectMapper.readTree("{\"source\":\"recommend\",\"limit\":6}");
        upsert.setDataJson(dataJson);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID));
    }

    @Test
    @DisplayName("TC-U007: createHomeSection i18n_json locale 键非法")
    void createHomeSection_invalidLocaleKey_throwsI18nInvalid() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("newsletter");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        JsonNode i18n = objectMapper.readTree("{\"zh\":{\"label\":\"测试\"}}");
        upsert.setI18nJson(i18n);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.I18N_JSON_INVALID));
    }

    @Test
    @DisplayName("TC-U008: updateHomeSection 不存在 → 404801")
    void updateHomeSection_notFound_throwsNotFound() {
        Long id = 999L;
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("custom");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        upsert.setVersion(0);

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
    }

    @Test
    @DisplayName("TC-U009: updateHomeSection 乐观锁冲突 → 409801")
    void updateHomeSection_versionConflict_throwsSortConflict() {
        Long id = 1L;
        HomePageSection existing = new HomePageSection();
        existing.setId(id);
        existing.setSectionType("custom");
        existing.setVersion(5);

        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("custom");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        upsert.setVersion(4);  // 不匹配

        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(id, upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT));
    }

    @Test
    @DisplayName("连续两次更新使用响应版本，均成功并返回数据库下一版本")
    void updateHomeSection_consecutiveUpdatesReturnNextVersion() {
        Long id = 1L;
        HomePageSection existing = new HomePageSection();
        existing.setId(id);
        existing.setSectionType("custom");
        existing.setEnabled(true);
        existing.setSortOrder(0);
        existing.setVersion(0);

        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("custom");
        upsert.setEnabled(true);
        upsert.setSortOrder(1);
        upsert.setVersion(0);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.updateByIdAndVersion(same(existing))).thenAnswer(invocation -> {
            existing.setVersion(existing.getVersion() + 1);
            return 1;
        });

        HomePageSectionDto first = service.update(id, upsert);
        upsert.setVersion(first.getVersion());
        HomePageSectionDto second = service.update(id, upsert);

        assertThat(first.getVersion()).isEqualTo(1);
        assertThat(second.getVersion()).isEqualTo(2);
        verify(cacheService, times(2)).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("TC-U010: toggleHomeSection 正常启用")
    void toggleHomeSection_enable_success() {
        Long id = 1L;
        HomePageSection existing = new HomePageSection();
        existing.setId(id);
        existing.setSectionType("custom");
        existing.setEnabled(false);
        existing.setVersion(0);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.updateEnabled(id, true, 0)).thenReturn(1);

        HomePageSectionDto result = service.toggle(id, true);

        assertThat(result.getEnabled()).isTrue();
        verify(cacheService).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("TC-U011: toggleHomeSection 不存在 → 404801")
    void toggleHomeSection_notFound_throwsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggle(999L, true))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
    }

    @Test
    @DisplayName("TC-U012: deleteHomeSection 正常删除")
    void deleteHomeSection_success() {
        Long id = 1L;
        HomePageSection existing = new HomePageSection();
        existing.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        verify(repository).deleteById(id);
        verify(cacheService).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("TC-U013: list 返回全部区块（按 sortOrder 排序）")
    void list_returnsAllSorted() {
        HomePageSection s1 = new HomePageSection();
        s1.setId(1L);
        s1.setSortOrder(0);
        HomePageSection s2 = new HomePageSection();
        s2.setId(2L);
        s2.setSortOrder(1);
        when(repository.findAllOrderBySort()).thenReturn(List.of(s1, s2));

        List<HomePageSectionDto> result = service.list(false);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("TC-U014: list enabledOnly=true 只返回启用区块")
    void list_enabledOnly_returnsEnabled() {
        HomePageSection s1 = new HomePageSection();
        s1.setId(1L);
        s1.setEnabled(true);
        when(repository.findEnabledOrderBySort()).thenReturn(List.of(s1));

        List<HomePageSectionDto> result = service.list(true);

        assertThat(result).hasSize(1);
        verify(repository).findEnabledOrderBySort();
    }

    @Test
    @DisplayName("TC-U015: batchSort 批量排序")
    void batchSort_success() {
        HomePageSection first = section(1L);
        HomePageSection second = section(2L);
        when(repository.findAllOrderById()).thenReturn(List.of(first, second));
        when(repository.batchUpdateSort(anyList())).thenReturn(2);

        service.batchSort(List.of(sortItem(1L, 2), sortItem(2L, 1)));

        verify(repository).findAllOrderById();
        verify(repository).batchUpdateSort(anyList());
        verify(cacheService).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("batchSort 拒绝空、重复、缺失字段及负排序值")
    void batchSort_rejectsMalformedItemsBeforeWrite() {
        assertSortValidation(null, "items");
        assertSortValidation(List.of(), "items");
        assertSortValidation(List.of(sortItem(null, 0)), "items");
        assertSortValidation(List.of(sortItem(1L, 0), sortItem(1L, 1)), "items");
        assertSortValidation(List.of(sortItem(1L, null)), "sort_order");
        assertSortValidation(List.of(sortItem(1L, -1)), "sort_order");

        verify(repository, never()).findAllOrderById();
        verify(repository, never()).batchUpdateSort(anyList());
        verify(cacheService, never()).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("batchSort 在分布式写锁内发现未知 id 返回 404801，且不产生部分更新")
    void batchSort_rejectsUnknownIdInsideWriteLock() {
        when(repository.findAllOrderById()).thenReturn(List.of(section(1L)));

        assertThatThrownBy(() -> service.batchSort(List.of(sortItem(1L, 0), sortItem(99L, 1))))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));

        verify(repository, never()).batchUpdateSort(anyList());
        verify(cacheService, never()).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("batchSort 更新行数不完整返回并发冲突，不失效缓存")
    void batchSort_rejectsIncompleteWrite() {
        when(repository.findAllOrderById()).thenReturn(List.of(section(1L), section(2L)));
        when(repository.batchUpdateSort(anyList())).thenReturn(1);

        assertThatThrownBy(() -> service.batchSort(List.of(sortItem(1L, 0), sortItem(2L, 1))))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT));

        verify(cacheService, never()).invalidateHomeSectionFamily();
    }

    private void assertSortValidation(List<SortItem> items, String field) {
        assertThatThrownBy(() -> service.batchSort(items))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> {
                    SiteBuilderException exception = (SiteBuilderException) ex;
                    assertThat(exception.getErrorCode()).isEqualTo(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH);
                    assertThat(exception.getDetails()).containsEntry("field", field);
                });
    }

    private static SortItem sortItem(Long id, Integer sortOrder) {
        SortItem item = new SortItem();
        item.setId(id);
        item.setSortOrder(sortOrder);
        return item;
    }

    private static HomePageSection section(Long id) {
        HomePageSection section = new HomePageSection();
        section.setId(id);
        return section;
    }

    @Test
    @DisplayName("整页保存读取稳定快照，并先释放旧 Hero 再写目标 Hero")
    void saveAllUsesStableSnapshotAndSafeHeroWriteOrder() {
        HomePageSection targetHero = new HomePageSection();
        targetHero.setId(1L);
        targetHero.setSectionType("custom");
        targetHero.setEnabled(true);
        targetHero.setSortOrder(1);
        targetHero.setVersion(0);
        HomePageSection oldHero = new HomePageSection();
        oldHero.setId(2L);
        oldHero.setSectionType("hero");
        oldHero.setEnabled(true);
        oldHero.setSortOrder(0);
        oldHero.setVersion(0);
        when(repository.findAllOrderById()).thenReturn(List.of(targetHero, oldHero));

        HomePageSaveItem releaseOldHero = new HomePageSaveItem();
        releaseOldHero.setId(2L);
        releaseOldHero.setSectionType("custom");
        releaseOldHero.setEnabled(true);
        releaseOldHero.setSortOrder(1);
        releaseOldHero.setVersion(0);
        HomePageSaveItem assignNewHero = new HomePageSaveItem();
        assignNewHero.setId(1L);
        assignNewHero.setSectionType("hero");
        assignNewHero.setEnabled(true);
        assignNewHero.setSortOrder(0);
        assignNewHero.setVersion(0);

        List<Long> writeIds = new ArrayList<>();
        when(repository.updateByIdAndVersion(any())).thenAnswer(invocation -> {
            HomePageSection section = invocation.getArgument(0);
            writeIds.add(section.getId());
            section.setVersion(section.getVersion() + 1);
            return 1;
        });

        service.saveAll(List.of(assignNewHero, releaseOldHero));

        verify(repository).findAllOrderById();
        assertThat(writeIds).containsExactly(2L, 1L);
        verify(cacheService).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("整页保存拒绝部分 payload 留下旧 Hero 后再创建第二个 Hero")
    void saveAllRejectsSecondHeroFromPartialPayload() {
        HomePageSection existingHero = section(1L);
        existingHero.setSectionType("hero");
        existingHero.setEnabled(true);
        existingHero.setSortOrder(0);
        existingHero.setVersion(0);
        HomePageSection custom = section(2L);
        custom.setSectionType("custom");
        custom.setEnabled(true);
        custom.setSortOrder(1);
        custom.setVersion(0);
        when(repository.findAllOrderById()).thenReturn(List.of(existingHero, custom));

        HomePageSaveItem secondHero = new HomePageSaveItem();
        secondHero.setId(2L);
        secondHero.setSectionType("hero");
        secondHero.setEnabled(true);
        secondHero.setSortOrder(1);
        secondHero.setVersion(0);

        assertThatThrownBy(() -> service.saveAll(List.of(secondHero)))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getDetails())
                        .containsEntry("reason", "homepage can contain only one hero section"));

        verify(repository, never()).updateByIdAndVersion(any());
        verify(cacheService, never()).invalidateHomeSectionFamily();
    }

    @Test
    @DisplayName("整页保存含 null 区块时返回 422801，不进入仓储")
    void saveAllRejectsNullItemBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.saveAll(java.util.Collections.singletonList(null)))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> {
                    SiteBuilderException failure = (SiteBuilderException) ex;
                    assertThat(failure.getErrorCode())
                            .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                    assertThat(failure.getDetails())
                            .containsEntry("reason", "homepage section item must not be null");
                });

        verifyNoInteractions(repository, cacheService);
    }

    @Test
    @DisplayName("TC-U016: get 不存在 → 404801")
    void get_notFound_throwsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(999L))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
    }

    @Test
    @DisplayName("TC-U017: createHomeSection theme_cards count 超范围")
    void createHomeSection_themeCardsCountOutOfRange_throwsDataJsonInvalid() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("theme_cards");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        JsonNode dataJson = objectMapper.readTree("{\"count\":10}");
        upsert.setDataJson(dataJson);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID));
    }

    @Test
    @DisplayName("TC-U018: createHomeSection product_rail limit 超范围")
    void createHomeSection_productRailLimitOutOfRange_throwsDataJsonInvalid() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("product_rail");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        JsonNode dataJson = objectMapper.readTree("{\"source\":\"new_arrival\",\"limit\":20}");
        upsert.setDataJson(dataJson);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class);
    }

    @Test
    @DisplayName("TC-U019: createHomeSection theme_cards count=1 合法")
    void createHomeSection_themeCardsCountOne_success() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("theme_cards");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        JsonNode dataJson = objectMapper.readTree("{\"count\":1}");
        upsert.setDataJson(dataJson);

        when(repository.insert(any())).thenAnswer(i -> {
            ((HomePageSection) i.getArgument(0)).setId(1L);
            return 1;
        });

        HomePageSectionDto result = service.create(upsert);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("TC-U020: createHomeSection hero 无 data_json 合法（KD-2 派生）")
    void createHomeSection_heroNoDataJson_success() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("hero");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        upsert.setDataJson(objectMapper.createObjectNode());
        upsert.setI18nJson(objectMapper.readTree("{\"en\":{},\"es\":{},\"fr\":{}}"));

        when(repository.insert(any())).thenAnswer(i -> {
            ((HomePageSection) i.getArgument(0)).setId(1L);
            return 1;
        });

        HomePageSectionDto result = service.create(upsert);
        assertThat(result.getSectionType()).isEqualTo("hero");
        assertThat(result.getDataJson()).isNull();
        assertThat(result.getI18nJson()).isNull();
    }

    @Test
    @DisplayName("TC-U021: createHomeSection hero 拒绝旧 banner_id 绑定")
    void createHomeSection_heroWithBannerId_throwsMismatch() throws Exception {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("hero");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        upsert.setDataJson(objectMapper.readTree("{\"banner_id\":42}"));
        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH));
        verify(repository, never()).insert(any());
    }
}
