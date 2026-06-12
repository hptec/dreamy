package com.dreamy.domain.flashsale.service;

import com.dreamy.domain.flashsale.entity.FlashSale;
import com.dreamy.domain.flashsale.entity.FlashSaleTranslation;
import com.dreamy.domain.flashsale.repository.FlashSaleRepository;
import com.dreamy.dto.StoreMarketingDtos.StoreFlashSale;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.port.CatalogQueryPort;
import com.dreamy.support.Translations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端闪购服务（E-MKT-09 listStoreFlashSales；FLOW-P15，s-761）。
 * 读路径以状态列为准不叠窗口过滤（到期翻转由 SCHED-MKT-01 保证——DEC-MKT-3）；TTL 60s 兜底倒计时新鲜度。
 * L2 TRACE: STEP-MKT-01~04 / CACHE-MKT-009 / MAP-MKT-011/012 / TASK-060。
 */
@Service
public class StoreFlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final MarketingCacheService cache;
    private final CatalogQueryPort catalogQueryPort;

    public StoreFlashSaleService(FlashSaleRepository flashSaleRepository, MarketingCacheService cache,
                                 CatalogQueryPort catalogQueryPort) {
        this.flashSaleRepository = flashSaleRepository;
        this.cache = cache;
        this.catalogQueryPort = catalogQueryPort;
    }

    /** E-MKT-09：active 活动（含 products + end_at 倒计时依据） */
    @SuppressWarnings("unchecked")
    public List<StoreFlashSale> list(String locale) {
        // STEP-MKT-01 查 JetCache marketing:flash:{locale}（TTL 60s）
        Object cached = cache.get(Family.FLASH, locale);
        if (cached instanceof List<?> hit) {
            return (List<StoreFlashSale>) hit;
        }
        // STEP-MKT-02 status='active' ORDER BY end_at ASC
        List<FlashSale> sales = flashSaleRepository.listStoreActive();
        // STEP-MKT-03 批查 nm → catalogQueryPort 装配 products[]（NP-MKT-002 批查防 N+1）
        Map<Long, List<Long>> productIds = flashSaleRepository.listProductIdsByFlashIds(
                sales.stream().map(FlashSale::getId).toList());
        // STEP-MKT-04 translation 覆盖 name → 写缓存 TTL 60s
        Map<Long, FlashSaleTranslation> translations = translationsFor(
                sales.stream().map(FlashSale::getId).toList(), locale);
        List<StoreFlashSale> items = new ArrayList<>(sales.size());
        for (FlashSale sale : sales) {
            List<Long> ids = productIds.getOrDefault(sale.getId(), List.of());
            List<CatalogQueryPort.ProductRef> products = ids.isEmpty()
                    ? List.of() : catalogQueryPort.listProductRefs(ids, locale);
            FlashSaleTranslation t = translations.get(sale.getId());
            items.add(new StoreFlashSale(sale.getId(),
                    Translations.coalesce(t == null ? null : t.getName(), sale.getName()),
                    sale.getDiscount(), sale.getStartAt(), sale.getEndAt(), products));
        }
        cache.put(Family.FLASH, locale, items);
        return items;
    }

    private Map<Long, FlashSaleTranslation> translationsFor(List<Long> ids, String locale) {
        Map<Long, FlashSaleTranslation> map = new HashMap<>();
        if (!Translations.needsTranslation(locale) || ids.isEmpty()) {
            return map;
        }
        for (FlashSaleTranslation row : flashSaleRepository.listTranslationsByFlashIds(ids)) {
            if (locale.equals(row.getLocale())) {
                map.put(row.getFlashSaleId(), row);
            }
        }
        return map;
    }
}
