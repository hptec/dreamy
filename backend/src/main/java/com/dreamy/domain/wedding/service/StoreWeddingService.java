package com.dreamy.domain.wedding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.wedding.entity.RealWedding;
import com.dreamy.domain.wedding.entity.RealWeddingTranslation;
import com.dreamy.domain.wedding.repository.RealWeddingRepository;
import com.dreamy.dto.StoreMarketingDtos.StoreRealWedding;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.port.CatalogQueryPort;
import com.dreamy.support.MarketingPaginatedSupport;
import com.dreamy.support.Translations;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端婚礼案例服务（E-MKT-04 listStoreWeddings / E-MKT-05 getStoreWedding，Shop the Look——ALIGN-011）。
 * L2 TRACE: STEP-MKT 各步 / CACHE-MKT-004/005 / MAP-MKT-006/012 / NP-MKT-002 / CV-MKT-006。
 */
@Service
public class StoreWeddingService {

    private final RealWeddingRepository weddingRepository;
    private final MarketingCacheService cache;
    private final CatalogQueryPort catalogQueryPort;

    public StoreWeddingService(RealWeddingRepository weddingRepository, MarketingCacheService cache,
                               CatalogQueryPort catalogQueryPort) {
        this.weddingRepository = weddingRepository;
        this.cache = cache;
        this.catalogQueryPort = catalogQueryPort;
    }

    /** E-MKT-04：published 列表（列表不带 products——契约「详情返回」） */
    @SuppressWarnings("unchecked")
    public Paginated<StoreRealWedding> page(int page, int pageSize, String locale) {
        // STEP-MKT-01 查 JetCache marketing:weddings:{page}:{page_size}:{locale}
        String cacheKey = page + ":" + pageSize + ":" + locale;
        MarketingCacheService.Lookup lookup = cache.lookup(Family.WEDDINGS, cacheKey);
        Object cached = lookup.value();
        if (cached instanceof Paginated<?> hit) {
            return (Paginated<StoreRealWedding>) hit;
        }
        // STEP-MKT-02 分页查询 ORDER BY wedding_date DESC
        Page<RealWedding> result = weddingRepository.pageStorePublished(page, pageSize);
        // STEP-MKT-03 translation 覆盖 title/story（DEC-MKT-1 回退 EN 主表列）
        Map<Long, RealWeddingTranslation> translations = translationsFor(
                result.getRecords().stream().map(RealWedding::getId).toList(), locale);
        Paginated<StoreRealWedding> paginated = MarketingPaginatedSupport.of(result,
                w -> toDto(w, translations.get(w.getId()), null));
        // STEP-MKT-04 写缓存
        cache.put(lookup, paginated);
        return paginated;
    }

    /** E-MKT-05：详情（含 products[]，Shop the Look——catalogQueryPort 单次批调） */
    public StoreRealWedding get(Long id, String locale) {
        // V-MKT-006 id 正整数（非法 → 404701 同口径）
        if (id == null || id <= 0) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-01 查 JetCache marketing:wedding:{id}:{locale}（null 值 60s）
        String cacheKey = id + ":" + locale;
        MarketingCacheService.Lookup lookup = cache.lookup(Family.WEDDING, cacheKey);
        Object cached = lookup.value();
        if (cache.isNullMarker(cached)) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        if (cached instanceof StoreRealWedding hit) {
            return hit;
        }
        // STEP-MKT-02 点查 published；不存在/draft → null 缓存 → 404701
        RealWedding wedding = weddingRepository.findByIdPublished(id);
        if (wedding == null) {
            cache.putNullMarker(lookup);
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-03 nm → catalogQueryPort.listProductRefs（仅 published 商品，缺失项静默剔除——CV-MKT-006）
        List<Long> productIds = weddingRepository.listProductIdsByWeddingId(id);
        List<CatalogQueryPort.ProductRef> products = productIds.isEmpty()
                ? List.of() : catalogQueryPort.listProductRefs(productIds, locale);
        // STEP-MKT-04 translation 覆盖 → 写缓存
        RealWeddingTranslation t = translationsFor(List.of(id), locale).get(id);
        StoreRealWedding dto = toDto(wedding, t, products);
        cache.put(lookup, dto);
        return dto;
    }

    private StoreRealWedding toDto(RealWedding w, RealWeddingTranslation t,
                                   List<CatalogQueryPort.ProductRef> products) {
        return new StoreRealWedding(w.getId(), w.getCouple(), w.getLocation(), w.getTheme(), w.getWeddingDate(),
                w.getCover(), PublishStatus.PUBLISHED.getKey(),
                Translations.coalesce(t == null ? null : t.getTitle(), w.getTitle()),
                Translations.coalesce(t == null ? null : t.getStory(), w.getStory()),
                products);
    }

    private Map<Long, RealWeddingTranslation> translationsFor(List<Long> ids, String locale) {
        Map<Long, RealWeddingTranslation> map = new HashMap<>();
        if (!Translations.needsTranslation(locale) || ids.isEmpty()) {
            return map;
        }
        for (RealWeddingTranslation row : weddingRepository.listTranslationsByWeddingIds(ids)) {
            if (locale.equals(row.getLocale())) {
                map.put(row.getRealWeddingId(), row);
            }
        }
        return map;
    }
}
