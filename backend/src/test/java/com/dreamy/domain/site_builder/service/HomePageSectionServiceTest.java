package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionDto;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionUpsert;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        service.batchSort(List.of(
                new com.dreamy.dto.SiteBuilderDtos.SortItem() {{
                    setId(1L);
                    setSortOrder(2);
                }},
                new com.dreamy.dto.SiteBuilderDtos.SortItem() {{
                    setId(2L);
                    setSortOrder(1);
                }}));

        verify(repository).batchSort(anyList());
        verify(cacheService).invalidateHomeSectionFamily();
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
    void createHomeSection_heroNoDataJson_success() {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("hero");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);

        when(repository.insert(any())).thenAnswer(i -> {
            ((HomePageSection) i.getArgument(0)).setId(1L);
            return 1;
        });

        HomePageSectionDto result = service.create(upsert);
        assertThat(result.getSectionType()).isEqualTo("hero");
    }
}
