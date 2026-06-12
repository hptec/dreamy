package com.dreamy.dto;

import com.dreamy.dto.MarketingTranslationDtos.BannerTranslationDto;
import com.dreamy.dto.MarketingTranslationDtos.BlogPostTranslationDto;
import com.dreamy.dto.MarketingTranslationDtos.CouponTranslationDto;
import com.dreamy.dto.MarketingTranslationDtos.FlashSaleTranslationDto;
import com.dreamy.dto.MarketingTranslationDtos.GuideTranslationDto;
import com.dreamy.dto.MarketingTranslationDtos.LookbookTranslationDto;
import com.dreamy.dto.MarketingTranslationDtos.RealWeddingTranslationDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台 DTO 集（openapi CouponUpsert/Coupon/FlashSaleUpsert/FlashSale/BannerUpsert/Banner/
 * BlogPostUpsert/BlogPost/RealWeddingUpsert/RealWedding/LookbookUpsert/Lookbook/GuideUpsert/Guide）。
 * Upsert 含 DEC-MKT-1 可选 EN 文案增量字段（additive，不改既有契约字段）；
 * used_count/clicks/views/published_at 只读（V-MKT-029/046/056：请求体提交一律忽略）。
 */
public final class AdminMarketingDtos {

    private AdminMarketingDtos() {
    }

    /** PATCH {id}/status 请求体（V-MKT-047/057/065/073/082） */
    public record StatusPatch(Integer status) {
    }

    /** openapi CouponUpsert（V-MKT-019~027；description=DEC-MKT-1 增量字段） */
    public record CouponUpsert(String code, String name, Integer type, String value, BigDecimal minAmount,
                               Integer totalLimit, LocalDateTime startAt, LocalDateTime endAt, Integer status,
                               String description, List<CouponTranslationDto> translations) {
    }

    /** openapi Coupon（used_count 只读核销计数） */
    public record CouponDto(Long id, String code, String name, Integer type, String value, BigDecimal minAmount,
                            Integer totalLimit, Integer usedCount, LocalDateTime startAt, LocalDateTime endAt,
                            Integer status, String description, List<CouponTranslationDto> translations) {
    }

    /** openapi FlashSaleUpsert（V-MKT-031~036） */
    public record FlashSaleUpsert(String name, String discount, LocalDateTime startAt, LocalDateTime endAt,
                                  Integer status, List<Long> productIds, List<FlashSaleTranslationDto> translations) {
    }

    /** openapi FlashSale */
    public record FlashSaleDto(Long id, String name, String discount, LocalDateTime startAt, LocalDateTime endAt,
                               Integer status, List<Long> productIds, List<FlashSaleTranslationDto> translations) {
    }

    /** openapi BannerUpsert（V-MKT-039~044；title/subtitle/cta_text=DEC-MKT-1 增量字段） */
    public record BannerUpsert(String name, String imageUrl, Integer position, LocalDateTime startTime,
                               LocalDateTime endTime, Integer status, Integer sort, String title, String subtitle,
                               String ctaText, List<BannerTranslationDto> translations) {
    }

    /** openapi Banner（clicks 只读） */
    public record BannerDto(Long id, String name, String imageUrl, Integer position, LocalDateTime startTime,
                            LocalDateTime endTime, Integer status, Integer sort, Integer clicks, String title,
                            String subtitle, String ctaText, List<BannerTranslationDto> translations) {
    }

    /** openapi BlogPostUpsert（V-MKT-050~054） */
    public record BlogPostUpsert(String title, String cover, String category, String author, String content,
                                 String slug, Integer status, List<BlogPostTranslationDto> translations) {
    }

    /** openapi BlogPost（published_at/views 只读） */
    public record BlogPostDto(Long id, String title, String cover, String category, String author, String content,
                              String slug, Integer status, LocalDateTime publishedAt, Integer views,
                              List<BlogPostTranslationDto> translations) {
    }

    /** openapi RealWeddingUpsert（V-MKT-059~063；title/story=DEC-MKT-1 增量字段） */
    public record RealWeddingUpsert(String couple, String location, String theme, String weddingDate, String cover,
                                    Integer status, String title, String story, List<Long> productIds,
                                    List<RealWeddingTranslationDto> translations) {
    }

    /** openapi RealWedding */
    public record RealWeddingDto(Long id, String couple, String location, String theme, String weddingDate,
                                 String cover, Integer status, String title, String story, List<Long> productIds,
                                 List<RealWeddingTranslationDto> translations) {
    }

    /** openapi LookbookUpsert（V-MKT-067~071；description=DEC-MKT-1 增量字段） */
    public record LookbookUpsert(String title, String theme, Integer status, String description,
                                 List<Long> productIds, List<LookbookTranslationDto> translations) {
    }

    /** openapi Lookbook */
    public record LookbookDto(Long id, String title, String theme, Integer status, String description,
                              List<Long> productIds, List<LookbookTranslationDto> translations) {
    }

    /** openapi GuideUpsert（V-MKT-075~080；body=DEC-MKT-1 增量字段） */
    public record GuideUpsert(String phase, String timeframe, String title, Integer tasksCount, Integer status,
                              String body, List<GuideTranslationDto> translations) {
    }

    /** openapi Guide */
    public record GuideDto(Long id, String phase, String timeframe, String title, Integer tasksCount, Integer status,
                           String body, List<GuideTranslationDto> translations) {
    }
}
