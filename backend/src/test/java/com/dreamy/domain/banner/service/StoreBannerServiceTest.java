package com.dreamy.domain.banner.service;

import com.dreamy.domain.banner.entity.Banner;
import com.dreamy.domain.banner.entity.BannerTranslation;
import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.dto.StoreMarketingDtos.StoreBanner;
import com.dreamy.enums.BannerPosition;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.infra.MarketingCacheService.Lookup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreBannerServiceTest {

    @Test
    @DisplayName("CTA 链接始终来自 Banner 主表，历史 translation 链接不会覆盖")
    void secondaryCtaLinkIsNeverTranslated() {
        BannerRepository repository = mock(BannerRepository.class);
        MarketingCacheService cache = mock(MarketingCacheService.class);
        StoreBannerService service = new StoreBannerService(repository, cache);
        Lookup lookup = new Lookup(Family.BANNERS, "1:es", 7L, null);

        Banner banner = new Banner();
        banner.setId(1L);
        banner.setName("Hero");
        banner.setImageUrl("/hero.jpg");
        banner.setPosition(BannerPosition.HERO);
        banner.setSort(0);
        banner.setCtaTextSecondary("Learn more");
        banner.setCtaLinkSecondary("/canonical-secondary-link");

        BannerTranslation translation = new BannerTranslation();
        translation.setBannerId(1L);
        translation.setLocale("es");
        translation.setCtaTextSecondary("Más información");
        translation.setCtaLinkSecondary("/legacy-translated-link");

        when(cache.lookup(Family.BANNERS, "1:es")).thenReturn(lookup);
        when(repository.listStoreActive(any(BannerPosition.class), any(LocalDateTime.class)))
                .thenReturn(List.of(banner));
        when(repository.listTranslationsByBannerIds(List.of(1L))).thenReturn(List.of(translation));

        StoreBanner result = service.list(BannerPosition.HERO, "es").getFirst();

        assertThat(result.ctaTextSecondary()).isEqualTo("Más información");
        assertThat(result.ctaLinkSecondary()).isEqualTo("/canonical-secondary-link");
    }
}
