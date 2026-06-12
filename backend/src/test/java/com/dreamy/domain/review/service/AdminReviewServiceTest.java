package com.dreamy.domain.review.service;

import com.dreamy.enums.ReviewBatchAction;
import com.dreamy.enums.ReviewStatus;
import com.dreamy.domain.review.entity.Review;
import com.dreamy.domain.review.entity.ReviewImage;
import com.dreamy.domain.review.repository.ReviewImageRepository;
import com.dreamy.domain.review.repository.ReviewRepository;
import com.dreamy.dto.ReviewDtos.BatchResult;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
import com.dreamy.infra.ReviewAfterCommitRunner;
import com.dreamy.infra.ReviewAuditRecorder;
import com.dreamy.infra.ReviewCacheService;
import com.dreamy.mq.ReviewEventPublisher;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.port.ReviewCatalogSnapshotPort.ProductBrief;
import com.dreamy.testsupport.ReviewImmediateTxRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台评价服务守卫/批量语义单元测试。
 * L2 TRACE: TC-REV-002 [P0]（批量 action 语义矩阵）/ TC-REV-011 单测面（409802）/
 * TC-REV-013 单测面（409803 + 幂等短路）/ TC-REV-014 单测面（409804 + 删除幂等 + reply_author 配置）/
 * TC-REV-015 单测面（404803 归属 + 幂等）/ TC-REV-020 单测面（精选/回复/图片不发 review.moderated）/
 * TC-REV-024~027 状态机 guard 单测面。
 */
@ExtendWith(MockitoExtension.class)
class AdminReviewServiceTest {

    private static final long PRODUCT = 11L;

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    ReviewImageRepository imageRepository;
    @Mock
    ReviewCatalogSnapshotPort catalogPort;
    @Mock
    ReviewCacheService cache;
    @Mock
    ReviewAuditRecorder audit;
    @Mock
    ReviewEventPublisher events;

    AdminReviewService service;

    @BeforeEach
    void setUp() {
        // ReviewAfterCommitRunner 真实实例：单测无事务同步 → 失效链立即执行，可对 events 断言
        service = new AdminReviewService(reviewRepository, imageRepository, catalogPort, cache, audit,
                new ReviewAfterCommitRunner(), events, new ReviewImmediateTxRunner(), new ObjectMapper(), "Dreamy Team");
        lenient().when(imageRepository.listByReviewIds(any(), eq(false))).thenReturn(List.of());
        lenient().when(catalogPort.getProductBriefs(any())).thenReturn(Map.of(
                PRODUCT, new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", true)));
        lenient().when(catalogPort.getProductBrief(PRODUCT))
                .thenReturn(new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", true));
    }

    private Review review(long id, ReviewStatus status, boolean featured) {
        Review r = new Review();
        r.setId(id);
        r.setProductId(PRODUCT);
        r.setUserId(7L);
        r.setStatus(status);
        r.setFeatured(featured);
        r.setRating(5);
        r.setSubmittedAt(LocalDateTime.now());
        return r;
    }

    // ==================== E-REV-07 审核 guard（TC-REV-011/024 单测面） ====================

    @Test
    @DisplayName("TC-REV-024 [P0]: pending→approved 审核成功，发 review.moderated + content.invalidated")
    void moderateApprove() {
        when(reviewRepository.findById(1L)).thenReturn(review(1L, ReviewStatus.PENDING, false));
        when(reviewRepository.casModerate(1L, ReviewStatus.APPROVED)).thenReturn(1);
        service.moderate(1L, "approved");
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_MODERATE), eq("review#1"), anyString());
        verify(events).publishModerated(PRODUCT, 1L, "approved");
        verify(events).publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED, "aurelia-gown", PRODUCT);
        verify(cache).invalidateProduct(ReviewCacheService.Family.REVIEWS, PRODUCT);
    }

    @Test
    @DisplayName("TC-REV-011 单测面 [P0]: 非 pending（CAS affected=0，bs-591 并发双审同型）→ 409802")
    void moderateNonPendingRejected() {
        when(reviewRepository.findById(1L)).thenReturn(review(1L, ReviewStatus.REJECTED, false));
        when(reviewRepository.casModerate(1L, ReviewStatus.APPROVED)).thenReturn(0);
        assertThatThrownBy(() -> service.moderate(1L, "approved"))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_STATE_INVALID));
        verify(events, never()).publishModerated(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("TC-REV-024 [P0]: status 枚举外/pending 目标值 → 422801；不存在 → 404801")
    void moderateValidation() {
        when(reviewRepository.findById(1L)).thenReturn(review(1L, ReviewStatus.PENDING, false));
        assertThatThrownBy(() -> service.moderate(1L, "__invalid__"))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.moderate(1L, "pending"))
                .isInstanceOf(ReviewException.class);
        when(reviewRepository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.moderate(99L, "approved"))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    // ==================== E-REV-08 精选 guard（TC-REV-013/026 单测面） ====================

    @Test
    @DisplayName("TC-REV-013 [P0]: pending 设精选 → 409803；approved → 成功且不发 review.moderated（TC-REV-020）")
    void featuredGuard() {
        when(reviewRepository.findById(2L)).thenReturn(review(2L, ReviewStatus.PENDING, false));
        when(reviewRepository.casSetFeatured(2L)).thenReturn(0);
        assertThatThrownBy(() -> service.setFeatured(2L, true))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.FEATURED_REQUIRES_APPROVED));

        when(reviewRepository.findById(3L)).thenReturn(review(3L, ReviewStatus.APPROVED, false));
        when(reviewRepository.casSetFeatured(3L)).thenReturn(1);
        service.setFeatured(3L, true);
        verify(events, never()).publishModerated(anyLong(), any(), anyString());
        verify(events).publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED, "aurelia-gown", PRODUCT);
    }

    @Test
    @DisplayName("TC-REV-013 [P0]: 同值幂等短路——不写审计不发事件不更新")
    void featuredIdempotent() {
        when(reviewRepository.findById(4L)).thenReturn(review(4L, ReviewStatus.APPROVED, true));
        service.setFeatured(4L, true);
        verify(reviewRepository, never()).casSetFeatured(anyLong());
        verify(audit, never()).record(anyString(), anyString(), any());
        verify(events, never()).publishContentInvalidated(anyString(), anyString(), anyLong());
    }

    // ==================== E-REV-09 批量语义矩阵（TC-REV-002/025/036 单测面） ====================

    @Test
    @DisplayName("TC-REV-002 [P0]: approve×{pending→更新, rejected→更新, approved→skipped}；不存在 id→skipped")
    void batchApproveMatrix() {
        when(reviewRepository.listByIds(any())).thenReturn(List.of(
                review(1L, ReviewStatus.PENDING, false),
                review(2L, ReviewStatus.REJECTED, false),
                review(3L, ReviewStatus.APPROVED, false)));
        when(reviewRepository.casBatchTransit(1L, ReviewStatus.PENDING, ReviewStatus.APPROVED)).thenReturn(1);
        when(reviewRepository.casBatchTransit(2L, ReviewStatus.REJECTED, ReviewStatus.APPROVED)).thenReturn(1);
        BatchResult result = service.batch(List.of(1L, 2L, 3L, 404L), "approve");
        assertThat(result.updatedIds()).containsExactly(1L, 2L);
        assertThat(result.skippedIds()).containsExactly(3L, 404L);
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_BATCH), eq("reviews/batch"), anyString());
        // approve → 按 product_id 去重发一次 review.moderated（该商品最后一条 review_id=2）
        verify(events).publishModerated(PRODUCT, 2L, "approved");
    }

    @Test
    @DisplayName("TC-REV-002/012 [P0]: reject×{pending/approved→更新（含精选行强制 featured 清零）, rejected→skipped}")
    void batchRejectMatrix() {
        when(reviewRepository.listByIds(any())).thenReturn(List.of(
                review(1L, ReviewStatus.PENDING, false),
                review(2L, ReviewStatus.APPROVED, true),
                review(3L, ReviewStatus.REJECTED, false)));
        when(reviewRepository.casBatchTransit(1L, ReviewStatus.PENDING, ReviewStatus.REJECTED)).thenReturn(1);
        when(reviewRepository.casBatchTransit(2L, ReviewStatus.APPROVED, ReviewStatus.REJECTED)).thenReturn(1);
        BatchResult result = service.batch(List.of(1L, 2L, 3L), "reject");
        assertThat(result.updatedIds()).containsExactly(1L, 2L);
        assertThat(result.skippedIds()).containsExactly(3L);
        // featured 清零由 RM-REV-012 reject 分支 SQL 承载（casBatchTransit set featured=0），此处断言调用形态
        verify(reviewRepository).casBatchTransit(2L, ReviewStatus.APPROVED, ReviewStatus.REJECTED);
    }

    @Test
    @DisplayName("TC-REV-002 [P0]: feature×{approved未精选→更新, 已精选/pending/rejected→skipped}；unfeature 同型")
    void batchFeatureMatrix() {
        when(reviewRepository.listByIds(any())).thenReturn(List.of(
                review(1L, ReviewStatus.APPROVED, false),
                review(2L, ReviewStatus.APPROVED, true),
                review(3L, ReviewStatus.PENDING, false),
                review(4L, ReviewStatus.REJECTED, false)));
        when(reviewRepository.casSetFeatured(1L)).thenReturn(1);
        BatchResult result = service.batch(List.of(1L, 2L, 3L, 4L), "feature");
        assertThat(result.updatedIds()).containsExactly(1L);
        assertThat(result.skippedIds()).containsExactly(2L, 3L, 4L);
        // feature 不发 review.moderated（rating 不变——TC-REV-020）
        verify(events, never()).publishModerated(anyLong(), any(), anyString());
        verify(events).publishContentInvalidated(ReviewEventPublisher.TYPE_REVIEW_CHANGED, "aurelia-gown", PRODUCT);

        // unfeature：featured=1→更新，featured=0→skipped
        when(reviewRepository.listByIds(any())).thenReturn(List.of(
                review(2L, ReviewStatus.APPROVED, true),
                review(1L, ReviewStatus.APPROVED, false)));
        when(reviewRepository.unsetFeatured(2L)).thenReturn(1);
        BatchResult unfeature = service.batch(List.of(2L, 1L), "unfeature");
        assertThat(unfeature.updatedIds()).containsExactly(2L);
        assertThat(unfeature.skippedIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("TC-REV-036 单测面 [P0]: ids 空 / >200 / 元素非法 / action 枚举外 → 422801")
    void batchValidation() {
        assertThatThrownBy(() -> service.batch(List.of(), "approve"))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.batch(null, "approve"))
                .isInstanceOf(ReviewException.class);
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList();
        assertThatThrownBy(() -> service.batch(tooMany, "approve"))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.batch(List.of(1L, 0L), "approve"))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.batch(List.of(1L), "__invalid__"))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    @DisplayName("TC-REV-002 [P0]: applyBatchAction 并发漂移（CAS affected=0）→ skipped")
    void batchConcurrentDriftSkipped() {
        Review pending = review(1L, ReviewStatus.PENDING, false);
        when(reviewRepository.casBatchTransit(1L, ReviewStatus.PENDING, ReviewStatus.APPROVED)).thenReturn(0);
        assertThat(service.applyBatchAction(ReviewBatchAction.APPROVE, pending)).isFalse();
    }

    // ==================== E-REV-10/11 回复 guard（TC-REV-014 单测面） ====================

    @Test
    @DisplayName("TC-REV-014 [P0]: 非 approved → 409804；approved → reply_author 取配置 'Dreamy Team' + 服务端时间")
    void replyGuard() {
        when(reviewRepository.findById(5L)).thenReturn(review(5L, ReviewStatus.PENDING, false));
        assertThatThrownBy(() -> service.putReply(5L, "thanks"))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.REPLY_REQUIRES_APPROVED));

        when(reviewRepository.findById(6L)).thenReturn(review(6L, ReviewStatus.APPROVED, false));
        service.putReply(6L, "  thanks for sharing  ");
        verify(reviewRepository).updateReply(eq(6L), eq("Dreamy Team"), eq("thanks for sharing"),
                any(LocalDateTime.class));
    }

    @Test
    @DisplayName("TC-REV-014 [P0]: reply trim 空 → 422801 fields.reply_content=blank；2001 超长拒绝")
    void replyValidation() {
        when(reviewRepository.findById(6L)).thenReturn(review(6L, ReviewStatus.APPROVED, false));
        assertThatThrownBy(() -> service.putReply(6L, "   "))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.putReply(6L, "a".repeat(2001)))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    @DisplayName("TC-REV-014 [P0]: 删除回复——有回复清空三字段；无回复幂等 204（不写审计不发事件）")
    void deleteReplyIdempotent() {
        Review withReply = review(7L, ReviewStatus.APPROVED, false);
        withReply.setReplyAuthor("Dreamy Team");
        withReply.setReplyContent("thanks");
        withReply.setReplyTime(LocalDateTime.now());
        when(reviewRepository.findById(7L)).thenReturn(withReply);
        service.deleteReply(7L);
        verify(reviewRepository).clearReply(7L);
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_MODERATE), eq("review#7"), anyString());

        when(reviewRepository.findById(8L)).thenReturn(review(8L, ReviewStatus.APPROVED, false));
        service.deleteReply(8L);
        verify(reviewRepository, never()).clearReply(8L);
    }

    // ==================== E-REV-12 图片驳回（TC-REV-015/027 单测面） ====================

    @Test
    @DisplayName("TC-REV-015 [P0]: imageId 不属于该评价 → 404803；shown→rejected 翻转 + 幂等短路（bs-594/595）")
    void imageOwnershipAndIdempotency() {
        when(reviewRepository.findById(9L)).thenReturn(review(9L, ReviewStatus.APPROVED, false));
        when(imageRepository.findByIdAndReviewId(100L, 9L)).thenReturn(null);
        assertThatThrownBy(() -> service.patchImage(9L, 100L, true))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_IMAGE_NOT_FOUND));

        ReviewImage image = new ReviewImage();
        image.setId(101L);
        image.setReviewId(9L);
        image.setUrl("http://localhost:9000/dreamy-media/review/1/a.jpg");
        image.setRejected(false);
        when(imageRepository.findByIdAndReviewId(101L, 9L)).thenReturn(image);
        // 翻转
        assertThat(service.patchImage(9L, 101L, true).rejected()).isTrue();
        verify(imageRepository).updateRejected(101L, true);
        // 幂等：目标值=当前值 → 不更新不审计不发事件
        org.mockito.Mockito.clearInvocations(imageRepository, audit, events);
        assertThat(service.patchImage(9L, 101L, false).rejected()).isFalse();
        verify(imageRepository, never()).updateRejected(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(audit, never()).record(anyString(), anyString(), any());
    }
}
