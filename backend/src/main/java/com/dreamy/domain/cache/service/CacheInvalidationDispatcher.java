package com.dreamy.domain.cache.service;

import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.ExchangeRateCacheService;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.ReviewCacheService;
import com.dreamy.infra.ShippingCacheService;
import com.dreamy.domain.site_builder.service.SiteBuilderCacheService;
import org.springframework.stereotype.Component;

/** Executes one strict local-cache target. Exceptions are intentionally propagated to the task worker. */
@Component
public class CacheInvalidationDispatcher {

    private final CatalogCacheService catalog;
    private final MarketingCacheService marketing;
    private final ReviewCacheService review;
    private final ShippingCacheService shipping;
    private final ExchangeRateCacheService exchangeRates;
    private final SiteBuilderCacheService siteBuilder;

    public CacheInvalidationDispatcher(CatalogCacheService catalog, MarketingCacheService marketing,
                                       ReviewCacheService review, ShippingCacheService shipping,
                                       ExchangeRateCacheService exchangeRates, SiteBuilderCacheService siteBuilder) {
        this.catalog = catalog;
        this.marketing = marketing;
        this.review = review;
        this.shipping = shipping;
        this.exchangeRates = exchangeRates;
        this.siteBuilder = siteBuilder;
    }

    public String execute(CacheInvalidationTarget target) {
        return switch (target) {
            case CATALOG_PRODUCTS -> generation(catalog.invalidateFamilyStrict(CatalogCacheService.Family.PRODUCTS));
            case CATALOG_PRODUCT -> generation(catalog.invalidateFamilyStrict(CatalogCacheService.Family.PRODUCT));
            case CATALOG_SEARCH -> generation(catalog.invalidateFamilyStrict(CatalogCacheService.Family.SEARCH));
            case CATALOG_RECO -> generation(catalog.invalidateFamilyStrict(CatalogCacheService.Family.RECO));
            case CATALOG_CATEGORIES -> generation(catalog.invalidateFamilyStrict(CatalogCacheService.Family.CATEGORIES));
            case CATALOG_COLLECTIONS -> generation(catalog.invalidateFamilyStrict(CatalogCacheService.Family.COLLECTIONS));
            case MARKETING_BANNERS -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.BANNERS));
            case MARKETING_BLOGS -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.BLOGS));
            case MARKETING_BLOG -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.BLOG));
            case MARKETING_WEDDINGS -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.WEDDINGS));
            case MARKETING_WEDDING -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.WEDDING));
            case MARKETING_LOOKBOOKS -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.LOOKBOOKS));
            case MARKETING_LOOKBOOK -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.LOOKBOOK));
            case MARKETING_GUIDES -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.GUIDES));
            case MARKETING_FLASH -> generation(marketing.invalidateFamilyStrict(MarketingCacheService.Family.FLASH));
            case SITE_HOME -> generation(siteBuilder.invalidateFamilyStrict(SiteBuilderCacheService.Family.HOME));
            case SITE_NAVIGATION -> generation(siteBuilder.invalidateFamilyStrict(SiteBuilderCacheService.Family.NAVIGATION));
            case SITE_FOOTER -> generation(siteBuilder.invalidateFamilyStrict(SiteBuilderCacheService.Family.FOOTER));
            case SITE_ANNOUNCEMENTS -> generation(siteBuilder.invalidateFamilyStrict(SiteBuilderCacheService.Family.ANNOUNCEMENTS));
            case REVIEW_REVIEWS -> generation(review.invalidateFamilyStrict(ReviewCacheService.Family.REVIEWS));
            case REVIEW_QUESTIONS -> generation(review.invalidateFamilyStrict(ReviewCacheService.Family.QUESTIONS));
            case SHIPPING_CARRIERS -> shipping.invalidateCarriersStrict();
            case SHIPPING_RATES -> shipping.invalidateRatesStrict();
            case TRADING_EXCHANGE_RATES -> exchangeRates.invalidateStrict();
        };
    }

    private String generation(long generation) {
        return "shared generation=" + generation;
    }
}
