package com.dreamy.marketing.domain.banner.service;

import com.dreamy.marketing.domain.banner.entity.Banner;
import com.dreamy.marketing.domain.banner.entity.BannerTranslation;
import com.dreamy.marketing.domain.banner.repository.BannerRepository;
import com.dreamy.marketing.domain.enums.BannerPosition;
import com.dreamy.marketing.domain.enums.ContentStatus;
import com.dreamy.marketing.dto.StoreMarketingDtos.StoreBanner;
import com.dreamy.marketing.infra.MarketingCacheService;
import com.dreamy.marketing.infra.MarketingCacheService.Family;
import com.dreamy.marketing.support.Translations;
import org.springframework.stereotype.Service;

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

    public StoreBannerService(BannerRepository bannerRepository, MarketingCacheService cache) {
        this.bannerRepository = bannerRepository;
        this.cache = cache;
    }

    /** E-MKT-01：投放清单（窗口过滤 + locale 翻译回退 + JetCache 300s，空集同样缓存） */
    @SuppressWarnings("unchecked")
    public List<StoreBanner> list(BannerPosition position, String locale) {
        // STEP-MKT-01 查 JetCache marketing:banners:{position|all}:{locale}
        String cacheKey = (position == null ? "all" : position.getKey()) + ":" + locale;
        Object cached = cache.get(Family.BANNERS, cacheKey);
        if (cached instanceof List<?> hit) {
            return (List<StoreBanner>) hit;
        }
        // STEP-MKT-02 窗口谓词查询（DEC-MKT-2 权威读口径），ORDER BY sort, id
        List<Banner> banners = bannerRepository.listStoreActive(position, LocalDateTime.now());
        // STEP-MKT-03 locale=es/fr → 批查 translation 覆盖 title/subtitle/cta_text，缺翻译回退 EN（DEC-MKT-1）
        Map<Long, BannerTranslation> translations = translationsFor(banners, locale);
        // STEP-MKT-04 装配 StoreBanner（不暴露 clicks/status/start_time/end_time）→ 写缓存
        List<StoreBanner> items = new ArrayList<>(banners.size());
        for (Banner b : banners) {
            BannerTranslation t = translations.get(b.getId());
            items.add(new StoreBanner(b.getId(), b.getName(), b.getImageUrl(), b.getPosition().getKey(), b.getSort(),
                    Translations.coalesce(t == null ? null : t.getTitle(), b.getTitle()),
                    Translations.coalesce(t == null ? null : t.getSubtitle(), b.getSubtitle()),
                    Translations.coalesce(t == null ? null : t.getCtaText(), b.getCtaText())));
        }
        cache.put(Family.BANNERS, cacheKey, items);
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
