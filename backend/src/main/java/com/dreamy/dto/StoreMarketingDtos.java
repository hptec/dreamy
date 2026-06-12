package com.dreamy.dto;

import com.dreamy.port.CatalogQueryPort.ProductRef;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消费端 DTO 集（文案已按 locale 解析——MAP-MKT-001~011；JSON 字段 snake_case 由全局 Jackson 策略保证）。
 * 全部 record 实现 Serializable：MarketingCacheService 走 JetCache Redis 远端层（Java 序列化编码）。
 */
public final class StoreMarketingDtos {

    private StoreMarketingDtos() {
    }

    /** openapi StoreBanner（MAP-MKT-001：不暴露 clicks/status/start_time/end_time） */
    public record StoreBanner(Long id, String name, String imageUrl, Integer position, Integer sort,
                              String title, String subtitle, String ctaText) implements Serializable {
    }

    /** openapi StoreBlogPostCard（MAP-MKT-003：excerpt——EN content strip 截断 200 / es·fr translation.excerpt 回退） */
    public record StoreBlogPostCard(Long id, String title, String slug, String cover, String category,
                                    String author, String excerpt, LocalDateTime publishedAt, Integer views) implements Serializable {
    }

    /** openapi StoreBlogPostDetail（MAP-MKT-004：content=translation.body 回退 EN；EN seo 派生） */
    public record StoreBlogPostDetail(Long id, String title, String slug, String cover, String category,
                                      String author, String excerpt, LocalDateTime publishedAt, Integer views,
                                      String content, String seoTitle, String seoDescription) implements Serializable {
    }

    /** openapi StoreRealWedding（MAP-MKT-006：status 恒 'published'；products 详情返回） */
    public record StoreRealWedding(Long id, String couple, String location, String theme, String weddingDate,
                                   String cover, Integer status, String title, String story,
                                   List<ProductRef> products) implements Serializable {
    }

    /** openapi StoreLookbook（MAP-MKT-008：products 详情返回） */
    public record StoreLookbook(Long id, String title, String theme, String description,
                                List<ProductRef> products) implements Serializable {
    }

    /** openapi StoreGuide（MAP-MKT-009） */
    public record StoreGuide(Long id, String phase, String timeframe, String title, String body,
                             Integer tasksCount) implements Serializable {
    }

    /** openapi StoreFlashSale（MAP-MKT-011：end_at 为前端倒计时依据） */
    public record StoreFlashSale(Long id, String name, String discount, LocalDateTime startAt,
                                 LocalDateTime endAt, List<ProductRef> products) implements Serializable {
    }

    /** openapi CouponValidateResponse（E-MKT-10：valid=false 时 coupon 仅在券存在时返回——不泄露码表） */
    public record CouponValidateResponse(boolean valid, Integer reasonCode, BigDecimal discountAmount,
                                         Boolean freeShipping, CouponBrief coupon) implements Serializable {
    }

    /** CouponValidateResponse.coupon（MAP-MKT-010：不暴露 used_count/total_limit/状态） */
    public record CouponBrief(String code, String name, Integer type, String value, BigDecimal minAmount) implements Serializable {
    }
}
