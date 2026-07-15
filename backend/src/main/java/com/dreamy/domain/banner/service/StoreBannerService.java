package com.dreamy.domain.banner.service;

import com.dreamy.domain.banner.entity.Banner;
import com.dreamy.domain.banner.entity.BannerTranslation;
import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.enums.BannerPosition;
import com.dreamy.enums.ContentStatus;
import com.dreamy.dto.StoreMarketingDtos.StoreBanner;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.support.Translations;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端 Banner 服务（E-MKT-01 listStoreBanners；FLOW-P01/P15）。
 * 投放窗口过滤为权威读口径（DEC-MKT-2：published 且 now∈[start_time,end_time)，空窗端开放，状态不翻转）。
 * L2 TRACE: E-MKT-01 STEP-MKT-01~04 / CACHE-MKT-001 / MAP-MKT-001 / TC-MKT-008。
 */
@Service
public class StoreBannerService {

    private final BannerRepository bannerRepository;
    private final MarketingCacheService cache;
    private final Clock clock;

    public StoreBannerService(BannerRepository bannerRepository, MarketingCacheService cache, Clock clock) {
        this.bannerRepository = bannerRepository;
        this.cache = cache;
        this.clock = clock;
    }

    /** E-MKT-01：投放清单（窗口过滤 + locale 翻译回退 + JetCache 300s，空集同样缓存） */
    @SuppressWarnings("unchecked")
    public List<StoreBanner> list(BannerPosition position, String locale) {
        // STEP-MKT-01 查 JetCache marketing:banners:{position|all}:{locale}
        String cacheKey = (position == null ? "all" : position.getKey()) + ":" + locale;
        MarketingCacheService.Lookup lookup = cache.lookup(Family.BANNERS, cacheKey);
        Object cached = lookup.value();
        if (cached instanceof List<?> hit) {
            return (List<StoreBanner>) hit;
        }
        // STEP-MKT-02 窗口谓词查询（DEC-MKT-2 权威读口径），ORDER BY sort, id
        List<Banner> banners = bannerRepository.listStoreActive(position, LocalDateTime.now(clock));
        // STEP-MKT-03 locale=es/fr → 批查 translation 覆盖 title/subtitle/cta_text，缺翻译回退 EN（DEC-MKT-1）
        Map<Long, BannerTranslation> translations = translationsFor(banners, locale);
        // STEP-MKT-04 装配 StoreBanner（不暴露 status/start_time/end_time）→ 写缓存
        List<StoreBanner> items = new ArrayList<>(banners.size());
        for (Banner b : banners) {
            BannerTranslation t = translations.get(b.getId());
            items.add(new StoreBanner(b.getId(), b.getName(),
                    Translations.coalesce(t == null ? null : t.getImageUrl(), b.getImageUrl()), b.getPosition().getKey(), b.getSort(),
                    Translations.coalesce(t == null ? null : t.getTitle(), b.getTitle()),
                    Translations.coalesce(t == null ? null : t.getSubtitle(), b.getSubtitle()),
                    Translations.coalesce(t == null ? null : t.getCtaText(), b.getCtaText()),
                    b.getCtaLink(),
                    Translations.coalesce(t == null ? null : t.getCtaTextSecondary(), b.getCtaTextSecondary()),
                    b.getCtaLinkSecondary()));
        }
        cache.put(lookup, items);
        return items;
    }

    /**
     * 投放窗口谓词（纯函数，DEC-MKT-2 / TC-MKT-008）：published 且
     * (start_time 空或 ≤now) 且 (end_time 空或 >now)；archived/draft 恒不出。
     */
    public static boolean inWindow(Banner banner, LocalDateTime now) {
        if (banner == null || banner.getStatus() != ContentStatus.PUBLISHED) {
            return false;
        }
        if (banner.getStartTime() != null && banner.getStartTime().isAfter(now)) {
            return false;
        }
        return banner.getEndTime() == null || banner.getEndTime().isAfter(now);
    }

    private Map<Long, BannerTranslation> translationsFor(List<Banner> banners, String locale) {
        Map<Long, BannerTranslation> map = new HashMap<>();
        if (!Translations.needsTranslation(locale) || banners.isEmpty()) {
            return map;
        }
        List<Long> ids = banners.stream().map(Banner::getId).toList();
        for (BannerTranslation row : bannerRepository.listTranslationsByBannerIds(ids)) {
            if (locale.equals(row.getLocale())) {
                map.put(row.getBannerId(), row);
            }
        }
        return map;
    }
}
