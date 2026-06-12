package com.dreamy.domain.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.ReviewBatchAction;
import com.dreamy.enums.ReviewStatus;
import com.dreamy.domain.review.entity.Review;
import com.dreamy.domain.review.entity.ReviewImage;
import com.dreamy.domain.review.repository.ReviewImageRepository;
import com.dreamy.domain.review.repository.ReviewRepository;
import com.dreamy.dto.AdminReviewListDTO;
import com.dreamy.dto.ReviewDtos.AdminReviewDto;
import com.dreamy.dto.ReviewDtos.BatchResult;
import com.dreamy.dto.ReviewDtos.ReviewImageDto;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
import com.dreamy.infra.ReviewAfterCommitRunner;
import com.dreamy.infra.ReviewAuditRecorder;
import com.dreamy.infra.ReviewCacheService;
import com.dreamy.infra.ReviewCacheService.Family;
import com.dreamy.infra.ReviewTxRunner;
import com.dreamy.mq.ReviewEventPublisher;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.support.ReviewFieldErrors;
import com.dreamy.support.ReviewParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台评价服务（E-REV-06~12；TX-REV-002~007；TASK-047/048 状态机 guard 内嵌）。
 * 失效链（CP-031 事务提交后）：@CacheInvalidate `review:reviews:{pid}:*` + MQ
 * review.moderated（仅审核态变更）/ content.invalidated（全部前台可见写）。
 * L2 TRACE: V-REV-014~030 / RM-REV-005~014/020~023 / CV-REV-007 / EVT-REV-001/002 / CACHE-REV-001。
 */
@Service
public class AdminReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository imageRepository;
    private final ReviewCatalogSnapshotPort catalogPort;
    private final ReviewCacheService cache;
    private final ReviewAuditRecorder audit;
    private final ReviewAfterCommitRunner afterCommit;
    private final ReviewEventPublisher events;
    private final ReviewTxRunner tx;
    private final ObjectMapper objectMapper;
    /** reply_author 固定写入操作者展示名（配置项缺省 "Dreamy Team"，与原型署名一致——E-REV-10 STEP-REV-03） */
    private final String replyAuthor;

    public AdminReviewService(ReviewRepository reviewRepository, ReviewImageRepository imageRepository,
                              ReviewCatalogSnapshotPort catalogPort, ReviewCacheService cache,
                              ReviewAuditRecorder audit, ReviewAfterCommitRunner afterCommit,
                              ReviewEventPublisher events, ReviewTxRunner tx, ObjectMapper objectMapper,
                              @Value("${dreamy.review.reply-author:Dreamy Team}") String replyAuthor) {
        this.reviewRepository = reviewRepository;
        this.imageRepository = imageRepository;
        this.catalogPort = catalogPort;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.events = events;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.replyAuthor = replyAuthor;
    }

    // ==================== E-REV-06 listAdminReviews ====================

    public AdminReviewListDTO listAdminReviews(Integer page, Integer pageSize, Integer status, Integer rating,
                                               Boolean featured, Long productId, String search) {
        // V-REV-014~019
        ReviewFieldErrors errors = new ReviewFieldErrors();
        int parsedPage = ReviewParams.parsePage(page, errors);
        int parsedSize = ReviewParams.parsePageSize(pageSize, errors);
        ReviewStatus statusFilter = null;
        if (status != null) {
            statusFilter = ReviewStatus.of(status);
            if (statusFilter == null) {
                errors.reject("status", "invalid_enum");
            }
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            errors.reject("rating", "range_invalid");
        }
        Long pid = ReviewParams.parsePositiveId(productId, "product_id", errors);
        String parsedSearch = ReviewParams.parseSearch(search, errors);
        errors.throwIfAny();

        // STEP-REV-01 组装条件分页
        Page<Review> reviewPage = reviewRepository.pageByAdminFilter(statusFilter, rating, featured, pid,
                parsedSearch, parsedPage, parsedSize);
        // STEP-REV-02 批查图片（全量含 rejected）+ product_name 批量派生（NP-REV-001）
        List<Long> reviewIds = reviewPage.getRecords().stream().map(Review::getId).toList();
        Map<Long, List<ReviewImageDto>> imagesByReview = groupImages(
                imageRepository.listByReviewIds(reviewIds, false));
        Set<Long> productIds = new LinkedHashSet<>();
        reviewPage.getRecords().forEach(r -> productIds.add(r.getProductId()));
        Map<Long, ReviewCatalogSnapshotPort.ProductBrief> briefs = catalogPort.getProductBriefs(productIds);
        // STEP-REV-03 pending_count 角标（不随筛选变化）
        long pendingCount = reviewRepository.countPending();
        // STEP-REV-04 MAP-REV-007 Paginated 子类 + pending_count 平铺
        AdminReviewListDTO dto = new AdminReviewListDTO();
        dto.setData(reviewPage.getRecords().stream()
                .map(r -> toAdminDto(r, imagesByReview.getOrDefault(r.getId(), List.of()), briefs))
                .toList());
        dto.setTotalElements(reviewPage.getTotal());
        dto.setPageNumber(parsedPage);
        dto.setPageSize(parsedSize);
        dto.setNumberOfElements(reviewPage.getRecords().size());
        dto.setTotalPages(parsedSize > 0 ? (int) Math.ceil((double) reviewPage.getTotal() / parsedSize) : 0);
        dto.setPendingCount(pendingCount);
        return dto;
    }

    // ==================== E-REV-07 patchAdminReviewStatus（review_moderation, FLOW-P14） ====================

    public AdminReviewDto moderate(Long id, Integer status) {
        // V-REV-020/021
        Review review = requireReview(id);
        ReviewStatus to = ReviewStatus.of(status);
        if (status == null || to == null || to == ReviewStatus.PENDING) {
            throw ReviewException.fieldValidation("status", "invalid_enum");
        }
        tx.inTx(() -> {
            // STEP-REV-02 CAS guard（并发双审防护 bs-591；reject 强制 featured=0——CV-REV-007）
            int affected = reviewRepository.casModerate(id, to);
            if (affected == 0) {
                throw new ReviewException(ReviewErrorCode.REVIEW_STATE_INVALID);
            }
            // STEP-REV-03 审计（事务内）
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("from", ReviewStatus.PENDING.getKey());
            changes.put("to", to.getKey());
            if (to == ReviewStatus.REJECTED && Boolean.TRUE.equals(review.getFeatured())) {
                changes.put("featured_forced", false);
            }
            audit.record(ReviewAuditRecorder.ACTION_MODERATE, "review#" + id, toJson(changes));
            // STEP-REV-04 提交后失效链 + MQ（review.moderated 仅审核态变更 → rating 回写；
            // content.invalidated → PDP 同步刷新；MQ 失败不回滚 EC-REV-002）
            Long productId = review.getProductId();
            afterCommit.run(() -> {
                cache.invalidateProduct(Family.REVIEWS, productId);
                events.publishModerated(productId, id, to.getKey());
                events.publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED,
                        slugOf(productId), productId);
            });
        });
        return readAdminDto(id);
    }

    // ==================== E-REV-08 patchAdminReviewFeatured ====================

    public AdminReviewDto setFeatured(Long id, Boolean featured) {
        Review review = requireReview(id);
        // V-REV-023
        if (featured == null) {
            throw ReviewException.fieldValidation("featured", "required");
        }
        // STEP-REV-02 幂等：目标值=当前值 → 直接返回当前行（不写审计不发事件，不开事务——TX-REV-003）
        if (featured.equals(review.getFeatured())) {
            return readAdminDto(id);
        }
        tx.inTx(() -> {
            // STEP-REV-03 CAS：true 仅 approved 可设（409803）；false 任意状态允许取消
            if (featured) {
                int affected = reviewRepository.casSetFeatured(id);
                if (affected == 0) {
                    throw new ReviewException(ReviewErrorCode.FEATURED_REQUIRES_APPROVED);
                }
            } else {
                reviewRepository.unsetFeatured(id);
            }
            // STEP-REV-04 审计归入「评价审核」（§0 归入规则）
            audit.record(ReviewAuditRecorder.ACTION_MODERATE, "review#" + id,
                    toJson(Map.of("featured", Map.of("from", !featured, "to", featured))));
            // STEP-REV-05 失效 + content.invalidated（不发 review.moderated——精选不改变 rating 聚合）
            Long productId = review.getProductId();
            afterCommit.run(() -> {
                cache.invalidateProduct(Family.REVIEWS, productId);
                events.publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED,
                        slugOf(productId), productId);
            });
        });
        return readAdminDto(id);
    }

    // ==================== E-REV-09 batchAdminReviews（batchSet, TX-REV-004） ====================

    public BatchResult batch(List<Long> ids, String action) {
        // V-REV-024 ids minItems 1，元素正整数，去重后处理，上限 200（防滥用）
        ReviewFieldErrors errors = new ReviewFieldErrors();
        Set<Long> deduped = new LinkedHashSet<>();
        if (ids == null || ids.isEmpty()) {
            errors.reject("ids", "required");
        } else {
            for (Long id : ids) {
                if (id == null || id <= 0) {
                    errors.reject("ids", "invalid");
                    break;
                }
                deduped.add(id);
            }
            if (deduped.size() > 200) {
                errors.reject("ids", "too_many");
            }
        }
        // V-REV-025 action
        ReviewBatchAction batchAction = ReviewBatchAction.of(action);
        if (batchAction == null) {
            errors.reject("action", "invalid_enum");
        }
        errors.throwIfAny();

        List<Long> updatedIds = new ArrayList<>();
        List<Long> skippedIds = new ArrayList<>();
        // 受影响商品集合（按 product_id 去重发事件；approve/reject 记录该商品最后一条 review_id）
        Map<Long, Long> moderatedProducts = new LinkedHashMap<>();
        Set<Long> touchedProducts = new LinkedHashSet<>();

        tx.inTx(() -> {
            // STEP-REV-01 批量读取分拣（不存在的 id 归入 skipped，批量语义不 404）
            Map<Long, Review> byId = new HashMap<>();
            for (Review r : reviewRepository.listByIds(deduped)) {
                byId.put(r.getId(), r);
            }
            for (Long id : deduped) {
                Review review = byId.get(id);
                if (review == null) {
                    skippedIds.add(id);
                    continue;
                }
                // STEP-REV-02/03 guard 分拣 + 逐条 CAS（并发漂移 affected=0 转 skipped）
                boolean updated = applyBatchAction(batchAction, review);
                if (updated) {
                    updatedIds.add(id);
                    touchedProducts.add(review.getProductId());
                    if (batchAction == ReviewBatchAction.APPROVE || batchAction == ReviewBatchAction.REJECT) {
                        moderatedProducts.put(review.getProductId(), id);
                    }
                } else {
                    skippedIds.add(id);
                }
            }
            // STEP-REV-04 审计
            audit.record(ReviewAuditRecorder.ACTION_BATCH, "reviews/batch", toJson(Map.of(
                    "action", batchAction.getKey(),
                    "updated_ids", updatedIds,
                    "skipped_ids", skippedIds)));
            // STEP-REV-05 updated 非空时提交后失效链 + 按 product_id 去重发事件
            if (!updatedIds.isEmpty()) {
                Integer statusKey = batchAction == ReviewBatchAction.APPROVE ? ReviewStatus.APPROVED.getKey()
                        : batchAction == ReviewBatchAction.REJECT ? ReviewStatus.REJECTED.getKey() : null;
                List<Long> products = new ArrayList<>(touchedProducts);
                Map<Long, Long> moderated = new LinkedHashMap<>(moderatedProducts);
                afterCommit.run(() -> {
                    for (Long pid : products) {
                        cache.invalidateProduct(Family.REVIEWS, pid);
                        String slug = slugOf(pid);
                        if (moderated.containsKey(pid)) {
                            events.publishModerated(pid, moderated.get(pid), statusKey);
                        }
                        events.publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED, slug, pid);
                    }
                });
            }
        });
        return new BatchResult(updatedIds, skippedIds);
    }

    /**
     * 批量 action 语义矩阵（TC-REV-002；state-machine batchSet 不限当前状态，guard 不满足跳过）：
     * approve：status≠approved → 置 approved；reject：status≠rejected → 置 rejected 且强制 featured=0；
     * feature：approved 且未精选 → 置 1（其余跳过——409803 批量语义=跳过不报错）；unfeature：featured=1 → 置 0。
     */
    boolean applyBatchAction(ReviewBatchAction action, Review review) {
        switch (action) {
            case APPROVE -> {
                if (review.getStatus() == ReviewStatus.APPROVED) {
                    return false;
                }
                return reviewRepository.casBatchTransit(review.getId(), review.getStatus(),
                        ReviewStatus.APPROVED) > 0;
            }
            case REJECT -> {
                if (review.getStatus() == ReviewStatus.REJECTED) {
                    return false;
                }
                return reviewRepository.casBatchTransit(review.getId(), review.getStatus(),
                        ReviewStatus.REJECTED) > 0;
            }
            case FEATURE -> {
                if (review.getStatus() != ReviewStatus.APPROVED || Boolean.TRUE.equals(review.getFeatured())) {
                    return false;
                }
                return reviewRepository.casSetFeatured(review.getId()) > 0;
            }
            case UNFEATURE -> {
                if (!Boolean.TRUE.equals(review.getFeatured())) {
                    return false;
                }
                return reviewRepository.unsetFeatured(review.getId()) > 0;
            }
        }
        return false;
    }

    // ==================== E-REV-10 putAdminReviewReply（TX-REV-005） ====================

    public AdminReviewDto putReply(Long id, String replyContent) {
        Review review = requireReview(id);
        // V-REV-027 trim 后 1..2000（js_guard replyDraft.trim() 后端兜底）
        String trimmed = replyContent == null ? "" : replyContent.trim();
        if (trimmed.isEmpty()) {
            throw ReviewException.fieldValidation("reply_content", "blank");
        }
        if (trimmed.length() > 2000) {
            throw ReviewException.fieldValidation("reply_content", "too_long");
        }
        tx.inTx(() -> {
            // STEP-REV-02 guard：仅 approved 可回复（409804）
            if (review.getStatus() != ReviewStatus.APPROVED) {
                throw new ReviewException(ReviewErrorCode.REPLY_REQUIRES_APPROVED);
            }
            // STEP-REV-03 UPSERT 语义（创建与编辑同一 PUT）
            reviewRepository.updateReply(id, replyAuthor, trimmed, LocalDateTime.now());
            // STEP-REV-04 审计归入「评价审核」
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("reply_before", review.getReplyContent());
            changes.put("reply_after", trimmed);
            audit.record(ReviewAuditRecorder.ACTION_MODERATE, "review#" + id, toJson(changes));
            // STEP-REV-05 失效 + content.invalidated
            Long productId = review.getProductId();
            afterCommit.run(() -> {
                cache.invalidateProduct(Family.REVIEWS, productId);
                events.publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED,
                        slugOf(productId), productId);
            });
        });
        return readAdminDto(id);
    }

    // ==================== E-REV-11 deleteAdminReviewReply（TX-REV-006） ====================

    public void deleteReply(Long id) {
        Review review = requireReview(id);
        // STEP-REV-02 幂等：已无回复 → 直接 204（不写审计不发事件，不开事务）
        if (review.getReplyContent() == null) {
            return;
        }
        tx.inTx(() -> {
            // STEP-REV-03 清空三字段
            reviewRepository.clearReply(id);
            // STEP-REV-04 审计归入「评价审核」
            audit.record(ReviewAuditRecorder.ACTION_MODERATE, "review#" + id, toJson(Map.of(
                    "reply_deleted", true,
                    "before", review.getReplyContent())));
            // STEP-REV-05 失效 + content.invalidated
            Long productId = review.getProductId();
            afterCommit.run(() -> {
                cache.invalidateProduct(Family.REVIEWS, productId);
                events.publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED,
                        slugOf(productId), productId);
            });
        });
    }

    // ==================== E-REV-12 patchAdminReviewImage（review_image_visibility, TX-REV-007） ====================

    public ReviewImageDto patchImage(Long id, Long imageId, Boolean rejected) {
        Review review = requireReview(id);
        // V-REV-030
        if (rejected == null) {
            throw ReviewException.fieldValidation("rejected", "required");
        }
        // STEP-REV-02 归属校验（不存在或不属于该评价 → 404803）
        ReviewImage image = imageRepository.findByIdAndReviewId(imageId, id);
        if (image == null) {
            throw new ReviewException(ReviewErrorCode.REVIEW_IMAGE_NOT_FOUND);
        }
        // STEP-REV-03 幂等：目标值=当前值 → 直接返回当前行（并发重复驳回/恢复只执行一次副作用，bs-594/595）
        if (rejected.equals(image.getRejected())) {
            return new ReviewImageDto(image.getId(), image.getUrl(), Boolean.TRUE.equals(image.getRejected()));
        }
        tx.inTx(() -> {
            // STEP-REV-04 shown↔rejected 双向翻转
            imageRepository.updateRejected(imageId, rejected);
            // STEP-REV-05 审计归入「评价审核」
            audit.record(ReviewAuditRecorder.ACTION_MODERATE, "review#" + id, toJson(Map.of(
                    "image_id", imageId,
                    "rejected", Map.of("from", !rejected, "to", rejected))));
            // STEP-REV-06 失效 + content.invalidated（驳回后前台立即不展示该图）
            Long productId = review.getProductId();
            afterCommit.run(() -> {
                cache.invalidateProduct(Family.REVIEWS, productId);
                events.publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED,
                        slugOf(productId), productId);
            });
        });
        return new ReviewImageDto(image.getId(), image.getUrl(), rejected);
    }

    // ==================== 装配/工具 ====================

    /** V-REV-020/022/026/028 口径：不存在（含非法 id）→ 404801 */
    private Review requireReview(Long id) {
        Review review = id == null || id <= 0 ? null : reviewRepository.findById(id);
        if (review == null) {
            throw new ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        return review;
    }

    /** 全量回读（E-REV-07/08/10 出参：AdminReview，images 含 rejected） */
    private AdminReviewDto readAdminDto(Long id) {
        Review review = requireReview(id);
        Map<Long, List<ReviewImageDto>> images = groupImages(
                imageRepository.listByReviewIds(List.of(id), false));
        Map<Long, ReviewCatalogSnapshotPort.ProductBrief> briefs =
                catalogPort.getProductBriefs(List.of(review.getProductId()));
        return toAdminDto(review, images.getOrDefault(id, List.of()), briefs);
    }

    /** MAP-REV-003（customer_name 不脱敏 + user_id + product_name 派生，商品已删除容忍 null） */
    private AdminReviewDto toAdminDto(Review review, List<ReviewImageDto> images,
                                      Map<Long, ReviewCatalogSnapshotPort.ProductBrief> briefs) {
        ReviewCatalogSnapshotPort.ProductBrief brief = briefs.get(review.getProductId());
        return new AdminReviewDto(review.getId(), review.getProductId(), review.getUserId(),
                brief == null ? null : brief.name(), review.getCustomerName(), review.getRating(),
                review.getContent(), review.getStatus() == null ? null : review.getStatus().getKey(),
                review.getFeatured(), review.getSubmittedAt(), images,
                review.getReplyAuthor(), review.getReplyContent(), review.getReplyTime());
    }

    private Map<Long, List<ReviewImageDto>> groupImages(List<ReviewImage> images) {
        Map<Long, List<ReviewImageDto>> result = new HashMap<>();
        for (ReviewImage image : images) {
            result.computeIfAbsent(image.getReviewId(), k -> new ArrayList<>())
                    .add(new ReviewImageDto(image.getId(), image.getUrl(),
                            Boolean.TRUE.equals(image.getRejected())));
        }
        return result;
    }

    /** 失效事件 slug（ReviewCatalogSnapshotPort；商品已删除返回 null → 跳过 content.invalidated） */
    private String slugOf(Long productId) {
        ReviewCatalogSnapshotPort.ProductBrief brief = catalogPort.getProductBrief(productId);
        return brief == null ? null : brief.slug();
    }

    private String toJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception ex) {
            return null;
        }
    }
}
