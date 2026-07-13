package com.dreamy.dto;

/**
 * 翻译行 DTO 集（locale ∈ {es,fr}，CV-MKT-007；admin 三语 tab 原样进出不回退合并——api-detail §0）。
 */
public final class MarketingTranslationDtos {

    private MarketingTranslationDtos() {
    }

    /** openapi CouponTranslation（V-MKT-027） */
    public record CouponTranslationDto(String locale, String name, String description) {
    }

    /** openapi FlashSaleTranslation（V-MKT-036） */
    public record FlashSaleTranslationDto(String locale, String name) {
    }

    /** openapi BannerTranslation（V-MKT-044） */
    public record BannerTranslationDto(String locale, String title, String subtitle, String ctaText,
                                       String ctaTextSecondary) {
    }

    /** openapi BlogPostTranslation（V-MKT-054） */
    public record BlogPostTranslationDto(String locale, String title, String excerpt, String body,
                                         String seoTitle, String seoDescription) {
    }

    /** openapi RealWeddingTranslation（V-MKT-063） */
    public record RealWeddingTranslationDto(String locale, String title, String story) {
    }

    /** openapi LookbookTranslation（V-MKT-071） */
    public record LookbookTranslationDto(String locale, String title, String description) {
    }

    /** openapi GuideTranslation（V-MKT-080） */
    public record GuideTranslationDto(String locale, String title, String body) {
    }
}
