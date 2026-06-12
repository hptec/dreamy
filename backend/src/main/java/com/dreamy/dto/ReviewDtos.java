package com.dreamy.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * review 域请求/响应 DTO（review-api.openapi.yml schema 对齐；JSON snake_case 由全局
 * PropertyNamingStrategies.SNAKE_CASE 承载，CP-001；时间 ISO8601，CP-014/MAP-REV-009）。
 * store 列表行/快照实现 Serializable（JetCache remote java 编解码，CACHE-REV-001/002）。
 */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    // ==================== 公共 ====================

    /** ReviewImage（契约 schema） */
    public record ReviewImageDto(Long id, String url, boolean rejected) implements Serializable {
    }

    // ==================== 消费端（MAP-REV-001/002/004） ====================

    /**
     * StoreReview：列表读取 customer_name 脱敏（MAP-REV-001）、images 仅 rejected=false；
     * 提交回执（MAP-REV-002）customer_name 原样、images 全量、status=pending。不暴露 user_id。
     */
    public record StoreReviewDto(
            Long id,
            Long productId,
            String customerName,
            Integer rating,
            String content,
            String status,
            Boolean featured,
            LocalDateTime submittedAt,
            List<ReviewImageDto> images,
            String replyAuthor,
            String replyContent,
            LocalDateTime replyTime
    ) implements Serializable {
    }

    /** StoreQuestion（MAP-REV-004：asker 脱敏；不暴露 visible/user_id） */
    public record StoreQuestionDto(
            Long id,
            Long productId,
            String asker,
            String question,
            LocalDateTime askedAt,
            String answer,
            LocalDateTime answerTime
    ) implements Serializable {
    }

    /**
     * MyReview（E-REV-16 / F-049 我的评价，L3 修复轮新增）：本人视角——customer_name 不输出
     * （本人无需）、全状态可见（pending/approved/rejected）、images 全量含 rejected 标记
     * （与 MAP-REV-002 本人回执同口径）、product 卡片简况（商品已删除容忍 slug/name=null）。
     */
    public record StoreMyReviewDto(
            Long id,
            Long productId,
            ProductCardDto product,
            Integer rating,
            String content,
            String status,
            Boolean featured,
            LocalDateTime submittedAt,
            List<ReviewImageDto> images,
            String replyAuthor,
            String replyContent,
            LocalDateTime replyTime
    ) {
    }

    /** 商品卡片简况（ReviewCatalogSnapshotPort.ProductBrief 裁剪：不外泄 published 内部态） */
    public record ProductCardDto(Long id, String slug, String name) {
    }

    // ==================== 后台（MAP-REV-003/005） ====================

    /** AdminReview：customer_name 不脱敏 + user_id + product_name 派生 + images 全量含 rejected */
    public record AdminReviewDto(
            Long id,
            Long productId,
            Long userId,
            String productName,
            String customerName,
            Integer rating,
            String content,
            String status,
            Boolean featured,
            LocalDateTime submittedAt,
            List<ReviewImageDto> images,
            String replyAuthor,
            String replyContent,
            LocalDateTime replyTime
    ) {
    }

    /** AdminQuestion：asker 不脱敏 + visible + product_name 派生 */
    public record AdminQuestionDto(
            Long id,
            Long productId,
            String productName,
            String asker,
            String question,
            LocalDateTime askedAt,
            String answer,
            LocalDateTime answerTime,
            String visible
    ) {
    }

    // ==================== 请求体 ====================

    /** StoreReviewCreate（E-REV-02：V-REV-004~007；请求体不接收 user_id——BE-DIM-6） */
    public record StoreReviewCreate(Long productId, Integer rating, String content, List<ImageRef> images) {
    }

    public record ImageRef(String url) {
    }

    /** E-REV-04（V-REV-010/011） */
    public record StoreQuestionCreate(Long productId, String question) {
    }

    /** E-REV-05（V-REV-012/013） */
    public record PresignRequest(String fileName, String contentType) {
    }

    public record PresignResponse(String uploadUrl, String objectKey, String publicUrl,
                                  OffsetDateTime expiresAt) {
    }

    /** E-REV-07（V-REV-021） */
    public record ReviewStatusPatch(String status) {
    }

    /** E-REV-08（V-REV-023） */
    public record FeaturedPatch(Boolean featured) {
    }

    /** E-REV-09（V-REV-024/025） */
    public record BatchRequest(List<Long> ids, String action) {
    }

    public record BatchResult(List<Long> updatedIds, List<Long> skippedIds) {
    }

    /** E-REV-10（V-REV-027） */
    public record ReplyPut(String replyContent) {
    }

    /** E-REV-12（V-REV-030） */
    public record ImageRejectPatch(Boolean rejected) {
    }

    /** E-REV-14（V-REV-035） */
    public record AnswerPut(String answer) {
    }

    /** E-REV-15（V-REV-037） */
    public record VisibilityPatch(String visible) {
    }

    // ==================== 缓存快照（Serializable，JetCache remote java 编解码） ====================

    /** E-REV-01 缓存值（CACHE-REV-001；空页同样缓存——穿透保护） */
    public record ReviewPageSnapshot(
            List<StoreReviewDto> items,
            long total,
            int page,
            int pageSize,
            BigDecimal ratingAvg,
            int ratingCount,
            Map<String, Integer> ratingBreakdown
    ) implements Serializable {
    }

    /** E-REV-03 缓存值（CACHE-REV-002） */
    public record QuestionPageSnapshot(
            List<StoreQuestionDto> items,
            long total,
            int page,
            int pageSize
    ) implements Serializable {
    }
}
