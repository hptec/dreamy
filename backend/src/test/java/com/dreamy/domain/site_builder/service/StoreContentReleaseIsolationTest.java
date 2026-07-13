package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.domain.banner.service.StoreBannerService;
import com.dreamy.domain.category.service.StoreCategoryService;
import com.dreamy.domain.product.service.RecommendationService;
import com.dreamy.domain.product.service.StoreProductService;
import com.dreamy.domain.site_builder.entity.HomePageRelease;
import com.dreamy.domain.site_builder.repository.AnnouncementRepository;
import com.dreamy.domain.site_builder.repository.FooterRepository;
import com.dreamy.domain.site_builder.repository.HomePageReleaseRepository;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.domain.site_builder.repository.NavigationItemRepository;
import com.dreamy.domain.wedding.service.StoreWeddingService;
import com.dreamy.dto.SiteBuilderDtos.StoreHomePageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreContentReleaseIsolationTest {

    @Mock private HomePageSectionRepository sectionRepository;
    @Mock private HomePageReleaseRepository releaseRepository;
    @Mock private NavigationItemRepository navigationRepository;
    @Mock private FooterRepository footerRepository;
    @Mock private AnnouncementRepository announcementRepository;
    @Mock private StoreBannerService bannerService;
    @Mock private BannerRepository bannerRepository;
    @Mock private StoreCategoryService categoryService;
    @Mock private StoreProductService productService;
    @Mock private RecommendationService recommendationService;
    @Mock private StoreWeddingService weddingService;

    private StoreContentService service;

    @BeforeEach
    void setUp() {
        service = new StoreContentService(sectionRepository, releaseRepository, navigationRepository,
                footerRepository, announcementRepository, new ObjectMapper(), bannerService, bannerRepository,
                categoryService, productService, recommendationService, weddingService);
    }

    @Test
    void publicHomepageReadsOnlyActiveReleaseSnapshot() {
        HomePageRelease release = new HomePageRelease();
        release.setId(9L);
        release.setReleaseNo(3);
        release.setContentEnJson("{\"sections\":[{\"sectionType\":\"custom\","
                + "\"data\":{\"heading\":\"Live snapshot\"}}]}");
        when(releaseRepository.findActive()).thenReturn(release);

        StoreHomePageDto page = service.getHome("en");

        assertThat(page.getReleaseNo()).isEqualTo(3);
        assertThat(page.getPreview()).isFalse();
        assertThat(page.getSections()).singleElement().satisfies(section -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) section.getData();
            assertThat(data).containsEntry("heading", "Live snapshot");
        });
        verify(sectionRepository, never()).findEnabledOrderBySort();
    }

    @Test
    void missingActiveReleaseFailsClosedInsteadOfExposingDraft() {
        when(releaseRepository.findActive()).thenReturn(null);

        StoreHomePageDto page = service.getHome("en");

        assertThat(page.getSections()).isEmpty();
        assertThat(page.getPreview()).isFalse();
        verify(sectionRepository, never()).findEnabledOrderBySort();
    }
}
