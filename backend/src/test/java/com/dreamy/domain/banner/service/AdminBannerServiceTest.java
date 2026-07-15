package com.dreamy.domain.banner.service;

import com.dreamy.domain.banner.entity.Banner;
import com.dreamy.domain.banner.entity.BannerTranslation;
import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.dto.AdminMarketingDtos.BannerUpsert;
import com.dreamy.enums.BannerPosition;
import com.dreamy.enums.ContentStatus;
import com.dreamy.infra.MarketingAuditRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBannerServiceTest {

    @Mock
    BannerRepository bannerRepository;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;
    @InjectMocks
    AdminBannerService service;

    @Test
    @DisplayName("Banner 后台列表原样返回 ES/FR 翻译供编辑窗口回填")
    void listReturnsTranslationsForEditing() {
        Banner banner = new Banner();
        banner.setId(1L);
        banner.setName("Hero");
        banner.setImageUrl("/hero.jpg");
        banner.setPosition(BannerPosition.HERO);
        banner.setStatus(ContentStatus.DRAFT);
        banner.setSort(0);

        BannerTranslation es = new BannerTranslation();
        es.setBannerId(1L);
        es.setLocale("es");
        es.setImageUrl("/hero-es.jpg");
        es.setTitle("Título");
        BannerTranslation fr = new BannerTranslation();
        fr.setBannerId(1L);
        fr.setLocale("fr");
        fr.setTitle("Titre");

        when(bannerRepository.listAdmin(null)).thenReturn(List.of(banner));
        when(bannerRepository.listTranslationsByBannerIds(List.of(1L))).thenReturn(List.of(es, fr));

        var result = service.list(null).getFirst();

        assertThat(result.translations()).hasSize(2);
        assertThat(result.translations()).anySatisfy(t -> {
            assertThat(t.locale()).isEqualTo("es");
            assertThat(t.imageUrl()).isEqualTo("/hero-es.jpg");
            assertThat(t.title()).isEqualTo("Título");
        });
        assertThat(result.translations()).anySatisfy(t -> {
            assertThat(t.locale()).isEqualTo("fr");
            assertThat(t.title()).isEqualTo("Titre");
        });
    }

    @Test
    @DisplayName("TX-MKT-009: Banner 删除先清译文再物理删除主表，并保留提交后失效")
    void deleteCleansTranslationsBeforePhysicalDelete() {
        Banner banner = new Banner();
        banner.setId(1L);
        banner.setName("Hero");
        banner.setStatus(ContentStatus.PUBLISHED);
        banner.setPosition(BannerPosition.HERO);
        when(bannerRepository.findById(1L)).thenReturn(banner);

        service.delete(1L);

        InOrder order = inOrder(bannerRepository);
        order.verify(bannerRepository).deleteTranslationsByBannerId(1L);
        order.verify(bannerRepository).deleteById(1L);
        verify(bannerRepository, never()).update(any());
        verify(audit).record("删除Banner", "Hero", null);
        verify(cacheTasks).enqueue(anyString(), eq("banner.delete"), eq("banner"), eq(1L), eq("Hero"),
                anyList(), nullable(java.time.LocalDateTime.class), anyMap(), nullable(String.class));
        verify(cacheTasks).cancelFuture("banner", 1L, "banner.window.");
    }

    @Test
    @DisplayName("Hero 编辑时重建 UTC 开始/结束边界任务")
    void updateReplacesFutureWindowTasks() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 3, 0);
        Banner banner = new Banner();
        banner.setId(7L);
        banner.setName("Old Hero");
        banner.setImageUrl("/old.jpg");
        banner.setPosition(BannerPosition.HERO);
        banner.setStatus(ContentStatus.PUBLISHED);
        banner.setSort(0);
        when(bannerRepository.findById(7L)).thenReturn(banner);
        when(cacheTasks.now()).thenReturn(now);
        BannerUpsert request = new BannerUpsert("Summer Hero", "/summer.jpg", BannerPosition.HERO.getKey(),
                now.plusHours(1), now.plusHours(3), ContentStatus.PUBLISHED.getKey(), 0,
                "Summer", null, null, null, null, null, List.of());

        service.update(7L, request);

        verify(cacheTasks).cancelFuture("banner", 7L, "banner.window.");
        verify(cacheTasks).enqueue(anyString(), eq("banner.window.start"), eq("banner"), eq(7L),
                eq("Summer Hero"), anyList(), eq(now.plusHours(1)), anyMap(), nullable(String.class));
        verify(cacheTasks).enqueue(anyString(), eq("banner.window.end"), eq("banner"), eq(7L),
                eq("Summer Hero"), anyList(), eq(now.plusHours(3)), anyMap(), nullable(String.class));
    }
}
