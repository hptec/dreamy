package com.dreamy.marketing.domain.lookbook.service;

import com.dreamy.marketing.domain.lookbook.entity.Lookbook;
import com.dreamy.marketing.domain.lookbook.entity.LookbookTranslation;
import com.dreamy.marketing.domain.lookbook.repository.LookbookRepository;
import com.dreamy.marketing.dto.StoreMarketingDtos.StoreLookbook;
import com.dreamy.marketing.error.MarketingErrorCode;
import com.dreamy.marketing.error.MarketingException;
import com.dreamy.marketing.infra.MarketingCacheService;
import com.dreamy.marketing.infra.MarketingCacheService.Family;
import com.dreamy.marketing.port.CatalogQueryPort;
import com.dreamy.marketing.support.Translations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端 Lookbook 服务（E-MKT-06 listStoreLookbooks / E-MKT-07 getStoreLookbook——ALIGN-012）。
 * L2 TRACE: STEP-MKT 各步 / CACHE-MKT-006/007 / MAP-MKT-008/012。
 */
@Service
public class StoreLookbookService {

    private final LookbookRepository lookbookRepository;
    private final MarketingCacheService cache;
    private final CatalogQueryPort catalogQueryPort;

    public StoreLookbookService(LookbookRepository lookbookRepository, MarketingCacheService cache,
                                CatalogQueryPort catalogQueryPort) {
        this.lookbookRepository = lookbookRepository;
        this.cache = cache;
        this.catalogQueryPort = catalogQueryPort;
    }

    /** E-MKT-06：published 列表（不带 products） */
    @SuppressWarnings("unchecked")
    public List<StoreLookbook> list(String locale) {
        Object cached = cache.get(Family.LOOKBOOKS, locale);
        if (cached instanceof List<?> hit) {
            return (List<StoreLookbook>) hit;
        }
        List<Lookbook> lookbooks = lookbookRepository.listStorePublished();
        Map<Long, LookbookTranslation> translations = translationsFor(
                lookbooks.stream().map(Lookbook::getId).toList(), locale);
        List<StoreLookbook> items = new ArrayList<>(lookbooks.size());
        for (Lookbook lb : lookbooks) {
            items.add(toDto(lb, translations.get(lb.getId()), null));
        }
        cache.put(Family.LOOKBOOKS, locale, items);
        return items;
    }

    /** E-MKT-07：详情（含 products[]） */
    public StoreLookbook get(Long id, String locale) {
        // V-MKT-006 口径：非法 id → 404701
        if (id == null || id <= 0) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        String cacheKey = id + ":" + locale;
        Object cached = cache.get(Family.LOOKBOOK, cacheKey);
        if (cache.isNullMarker(cached)) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        if (cached instanceof StoreLookbook hit) {
            return hit;
        }
        Lookbook lookbook = lookbookRepository.findByIdPublished(id);
        if (lookbook == null) {
            cache.putNullMarker(Family.LOOKBOOK, cacheKey);
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-03 nm → catalogQueryPort（同 E-MKT-05 剔除口径，单次批调 NP-MKT-002）
        List<Long> productIds = lookbookRepository.listProductIdsByLookbookId(id);
        List<CatalogQueryPort.ProductRef> products = productIds.isEmpty()
                ? List.of() : catalogQueryPort.listProductRefs(productIds, locale);
        LookbookTranslation t = translationsFor(List.of(id), locale).get(id);
        StoreLookbook dto = toDto(lookbook, t, products);
        cache.put(Family.LOOKBOOK, cacheKey, dto);
        return dto;
    }

    private StoreLookbook toDto(Lookbook lb, LookbookTranslation t, List<CatalogQueryPort.ProductRef> products) {
        return new StoreLookbook(lb.getId(),
                Translations.coalesce(t == null ? null : t.getTitle(), lb.getTitle()),
                lb.getTheme(),
                Translations.coalesce(t == null ? null : t.getDescription(), lb.getDescription()),
                products);
    }

    private Map<Long, LookbookTranslation> translationsFor(List<Long> ids, String locale) {
        Map<Long, LookbookTranslation> map = new HashMap<>();
        if (!Translations.needsTranslation(locale) || ids.isEmpty()) {
            return map;
        }
        for (LookbookTranslation row : lookbookRepository.listTranslationsByLookbookIds(ids)) {
            if (locale.equals(row.getLocale())) {
                map.put(row.getLookbookId(), row);
            }
        }
        return map;
    }
}
