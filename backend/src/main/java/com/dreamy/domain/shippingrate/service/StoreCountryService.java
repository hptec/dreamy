package com.dreamy.domain.shippingrate.service;

import com.dreamy.dto.TradingDtos.CountryDto;
import com.dreamy.dto.TradingDtos.RegionDto;
import com.dreamy.infra.ShippingCacheService;
import com.dreamy.support.CountryCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 消费端国家列表（order-flow-complete D）：CountryCatalog 全量 + supported（zone 有启用运费选项或 REST 兜底）；
 * JetCache shipping:countries（运费选项/承运商写后失效）。
 */
@Service
public class StoreCountryService {

    private final ShippingQuoteService quoteService;
    private final ShippingCacheService cache;

    public StoreCountryService(ShippingQuoteService quoteService, ShippingCacheService cache) {
        this.quoteService = quoteService;
        this.cache = cache;
    }

    public List<CountryDto> list() {
        return cache.getCountries(this::load);
    }

    List<CountryDto> load() {
        List<CountryDto> items = new ArrayList<>();
        for (CountryCatalog.Country country : CountryCatalog.all()) {
            boolean supported = quoteService.zoneSupported(country.zone());
            List<RegionDto> regions = country.regions().stream()
                    .map(r -> new RegionDto(r.code(), r.name())).toList();
            items.add(new CountryDto(country.code(), country.name(), country.zone(), supported, regions));
        }
        return items;
    }
}
