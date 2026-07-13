package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.domain.site_builder.entity.HomePagePreviewToken;
import com.dreamy.domain.site_builder.entity.HomePageRelease;
import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.repository.HomePageReleaseRepository;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.dto.SiteBuilderDtos.HomePagePreviewTokenDto;
import com.dreamy.dto.SiteBuilderDtos.HomePagePublicationStatusDto;
import com.dreamy.dto.SiteBuilderDtos.HomePageReleaseDto;
import com.dreamy.dto.SiteBuilderDtos.StoreHomePageDto;
import com.dreamy.dto.SiteBuilderDtos.StoreHomeSectionDto;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomePagePublicationServiceTest {

    @Mock private HomePageSectionRepository sectionRepository;
    @Mock private HomePageReleaseRepository releaseRepository;
    @Mock private BannerRepository bannerRepository;
    @Mock private StoreContentService storeContentService;
    @Mock private SiteBuilderCacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HomePagePublicationService service;
    private HomePageSection section;

    @BeforeEach
    void setUp() {
        service = new HomePagePublicationService(sectionRepository, releaseRepository, bannerRepository,
                storeContentService, cacheService, objectMapper);
        section = new HomePageSection();
        section.setId(11L);
        section.setSectionType("custom");
        section.setEnabled(true);
        section.setSortOrder(0);
        section.setDataJson("{\"image_url\":\"/draft.jpg\"}");
        section.setI18nJson("{\"en\":{\"heading\":\"Draft\"}}");
        section.setLabel("Custom");
        section.setVersion(3);
        lenient().when(sectionRepository.findEnabledOrderBySort()).thenReturn(List.of(section));
        lenient().when(sectionRepository.findAllOrderBySort()).thenReturn(List.of(section));
        lenient().when(storeContentService.getDraftHome(anyString())).thenAnswer(invocation -> emptyPage());
    }

    @Test
    void publishCreatesImmutableSnapshotAndAtomicallyActivatesIt() {
        when(releaseRepository.nextReleaseNo()).thenReturn(1);
        when(releaseRepository.findActive()).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<HomePageRelease>getArgument(0).setId(101L);
            return null;
        }).when(releaseRepository).insert(any(HomePageRelease.class));

        HomePagePublicationStatusDto status = service.status();
        HomePageReleaseDto published = service.publish("July homepage", status.getDraftRevision());

        ArgumentCaptor<HomePageRelease> captor = ArgumentCaptor.forClass(HomePageRelease.class);
        verify(releaseRepository).insert(captor.capture());
        HomePageRelease snapshot = captor.getValue();
        assertThat(snapshot.getSnapshotJson()).contains("draft.jpg");
        assertThat(snapshot.getContentEnJson()).isNotBlank();
        assertThat(snapshot.getContentEsJson()).isNotBlank();
        assertThat(snapshot.getContentFrJson()).isNotBlank();
        assertThat(snapshot.getName()).isEqualTo("July homepage");
        assertThat(published.getReleaseNo()).isEqualTo(1);
        verify(releaseRepository).activate(101L);
        verify(cacheService).invalidateHomeSectionFamily();
    }

    @Test
    void publishRejectsWorkspaceChangedAfterConfirmation() {
        assertThatThrownBy(() -> service.publish("stale", "outdated-revision"))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(error -> assertThat(((SiteBuilderException) error).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_RELEASE_DRAFT_CHANGED));

        verify(releaseRepository, never()).insert(any());
        verify(releaseRepository, never()).activate(any());
    }

    @Test
    void statusIgnoresEquivalentJsonNumberFormatting() throws Exception {
        StoreHomeSectionDto numericSection = new StoreHomeSectionDto();
        numericSection.setSectionType("custom");
        numericSection.setData(Map.of("price", new BigDecimal("1280.00")));
        StoreHomePageDto numericPage = emptyPage();
        numericPage.setSections(List.of(numericSection));
        when(storeContentService.getDraftHome(anyString())).thenReturn(numericPage);
        when(releaseRepository.nextReleaseNo()).thenReturn(1);
        when(releaseRepository.findActive()).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<HomePageRelease>getArgument(0).setId(101L);
            return null;
        }).when(releaseRepository).insert(any(HomePageRelease.class));
        HomePagePublicationStatusDto initial = service.status();
        service.publish("numbers", initial.getDraftRevision());

        ArgumentCaptor<HomePageRelease> captor = ArgumentCaptor.forClass(HomePageRelease.class);
        verify(releaseRepository).insert(captor.capture());
        HomePageRelease active = captor.getValue();
        assertThat(active.getContentEnJson()).contains("1280.00");
        active.setContentEnJson(active.getContentEnJson().replace("1280.00", "1280.0"));
        when(releaseRepository.findActive()).thenReturn(active);

        assertThat(service.status().isDraftModified()).isFalse();
    }

    @Test
    void previewTokenStoresOnlyHashAndReadsFrozenSnapshot() {
        ArgumentCaptor<HomePagePreviewToken> captor = ArgumentCaptor.forClass(HomePagePreviewToken.class);
        doAnswer(invocation -> null).when(releaseRepository).insertPreviewToken(captor.capture());

        LocalDateTime before = LocalDateTime.now();
        HomePagePreviewTokenDto issued = service.createPreviewToken();
        HomePagePreviewToken stored = captor.getValue();

        assertThat(issued.getToken()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(issued.getToken());
        assertThat(stored.getSnapshotJson()).contains("draft.jpg");
        assertThat(stored.getExpiresAt()).isBetween(before.plusMinutes(29), before.plusMinutes(31));

        when(releaseRepository.findValidPreviewToken(eq(stored.getTokenHash()), any(LocalDateTime.class)))
                .thenReturn(stored);
        StoreHomePageDto frozen = emptyPage();
        when(storeContentService.readContentJson(stored.getContentEnJson(), true)).thenReturn(frozen);

        assertThat(service.previewByToken(issued.getToken(), "en")).isSameAs(frozen);
        verify(storeContentService).readContentJson(stored.getContentEnJson(), true);
    }

    @Test
    void invalidOrExpiredPreviewTokenNeverFallsBackToDraft() {
        when(releaseRepository.findValidPreviewToken(anyString(), any(LocalDateTime.class))).thenReturn(null);

        assertThatThrownBy(() -> service.previewByToken("a".repeat(43), "en"))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(error -> assertThat(((SiteBuilderException) error).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.HOME_PREVIEW_TOKEN_INVALID));
        verify(storeContentService, never()).readContentJson(anyString(), anyBoolean());
    }

    @Test
    void rollbackCreatesNewReleaseAndRestoresWorkspace() {
        HomePageRelease source = new HomePageRelease();
        source.setId(7L);
        source.setReleaseNo(4);
        source.setName("Old version");
        source.setSnapshotJson("[{\"sectionType\":\"custom\",\"enabled\":true,\"sortOrder\":0,"
                + "\"dataJson\":{\"image_url\":\"/old.jpg\"},"
                + "\"i18nJson\":{\"en\":{\"heading\":\"Old\"}},\"label\":\"Old\"}]");
        source.setContentEnJson("{\"sections\":[]}");
        source.setContentEsJson("{\"sections\":[]}");
        source.setContentFrJson("{\"sections\":[]}");
        when(releaseRepository.findById(7L)).thenReturn(source);
        when(releaseRepository.nextReleaseNo()).thenReturn(5);
        doAnswer(invocation -> {
            invocation.<HomePageRelease>getArgument(0).setId(8L);
            return null;
        }).when(releaseRepository).insert(any(HomePageRelease.class));

        HomePageReleaseDto rolledBack = service.rollback(7L);

        ArgumentCaptor<HomePageRelease> releaseCaptor = ArgumentCaptor.forClass(HomePageRelease.class);
        verify(releaseRepository).insert(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().getSourceReleaseId()).isEqualTo(7L);
        assertThat(releaseCaptor.getValue().getSnapshotJson()).isEqualTo(source.getSnapshotJson());
        assertThat(rolledBack.getReleaseNo()).isEqualTo(5);
        verify(releaseRepository).activate(8L);

        ArgumentCaptor<List<HomePageSection>> workspaceCaptor = ArgumentCaptor.forClass(List.class);
        verify(sectionRepository).replaceAll(workspaceCaptor.capture());
        assertThat(workspaceCaptor.getValue()).singleElement().satisfies(restored -> {
            assertThat(restored.getSectionType()).isEqualTo("custom");
            assertThat(restored.getDataJson()).contains("old.jpg");
            assertThat(restored.getVersion()).isZero();
        });
    }

    private StoreHomePageDto emptyPage() {
        StoreHomePageDto page = new StoreHomePageDto();
        page.setSections(List.of());
        page.setPreview(true);
        return page;
    }
}
