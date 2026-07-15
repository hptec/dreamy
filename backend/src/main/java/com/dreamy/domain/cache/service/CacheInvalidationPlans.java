package com.dreamy.domain.cache.service;

import java.util.List;

import static com.dreamy.domain.cache.service.CacheInvalidationTarget.*;

/** Reusable target plans keep write-operation coverage explicit and reviewable. */
public final class CacheInvalidationPlans {
    private CacheInvalidationPlans() {}

    public static final List<CacheInvalidationTarget> PRODUCT_FULL = List.of(
            CATALOG_PRODUCT, CATALOG_PRODUCTS, CATALOG_SEARCH, CATALOG_RECO,
            CATALOG_CATEGORIES, CATALOG_COLLECTIONS,
            MARKETING_WEDDINGS, MARKETING_WEDDING, MARKETING_LOOKBOOKS, MARKETING_LOOKBOOK, MARKETING_FLASH,
            SITE_HOME);
    public static final List<CacheInvalidationTarget> PRODUCT_FLAGS = List.of(
            CATALOG_PRODUCT, CATALOG_PRODUCTS, CATALOG_SEARCH, CATALOG_RECO, SITE_HOME);
    public static final List<CacheInvalidationTarget> CATEGORY_CREATE = List.of(
            CATALOG_CATEGORIES, CATALOG_PRODUCTS, SITE_HOME);
    public static final List<CacheInvalidationTarget> CATEGORY_UPDATE = List.of(
            CATALOG_CATEGORIES, CATALOG_PRODUCTS, CATALOG_PRODUCT, SITE_HOME);
    public static final List<CacheInvalidationTarget> CATEGORY_DELETE = List.of(CATALOG_CATEGORIES, SITE_HOME);
    public static final List<CacheInvalidationTarget> COLLECTION = List.of(
            CATALOG_COLLECTIONS, CATALOG_PRODUCTS, CATALOG_SEARCH, CATALOG_RECO, SITE_HOME);
    public static final List<CacheInvalidationTarget> ATTRIBUTE = List.of(CATALOG_PRODUCTS, CATALOG_PRODUCT);
    public static final List<CacheInvalidationTarget> BANNER = List.of(MARKETING_BANNERS, SITE_HOME);
    public static final List<CacheInvalidationTarget> FLASH = List.of(MARKETING_FLASH);
    public static final List<CacheInvalidationTarget> BLOG = List.of(MARKETING_BLOGS, MARKETING_BLOG);
    public static final List<CacheInvalidationTarget> WEDDING = List.of(
            MARKETING_WEDDINGS, MARKETING_WEDDING, SITE_HOME);
    public static final List<CacheInvalidationTarget> LOOKBOOK = List.of(MARKETING_LOOKBOOKS, MARKETING_LOOKBOOK);
    public static final List<CacheInvalidationTarget> GUIDE = List.of(MARKETING_GUIDES);
    public static final List<CacheInvalidationTarget> REVIEW = List.of(REVIEW_REVIEWS);
    public static final List<CacheInvalidationTarget> QUESTION = List.of(REVIEW_QUESTIONS);
    public static final List<CacheInvalidationTarget> PRODUCT_SALES = List.of(CATALOG_RECO, SITE_HOME);
    public static final List<CacheInvalidationTarget> SITE_HOME_PLAN = List.of(SITE_HOME);
    public static final List<CacheInvalidationTarget> SITE_NAVIGATION_PLAN = List.of(SITE_NAVIGATION);
    public static final List<CacheInvalidationTarget> SITE_FOOTER_PLAN = List.of(SITE_FOOTER);
    public static final List<CacheInvalidationTarget> SITE_ANNOUNCEMENTS_PLAN = List.of(SITE_ANNOUNCEMENTS);
}
