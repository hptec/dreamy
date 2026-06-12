package com.dreamy.domain.review.service;

import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.storage.StorageProperties;
import com.dreamy.enums.ReviewStatus;
import com.dreamy.domain.review.entity.Review;
import com.dreamy.domain.review.repository.ReviewImageRepository;
import com.dreamy.domain.review.repository.ReviewRepository;
import com.dreamy.dto.ReviewDtos.ImageRef;
import com.dreamy.dto.ReviewDtos.StoreReviewCreate;
import com.dreamy.dto.ReviewDtos.StoreReviewDto;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
import com.dreamy.infra.ReviewCacheService;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.port.ReviewCatalogSnapshotPort.ProductBrief;
import com.dreamy.port.ReviewIdentityQueryPort;
import com.dreamy.port.TradingPurchaseQueryPort;
import com.dreamy.testsupport.ReviewImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-REV-02 提交评价守卫单元测试。
 * L2 TRACE: TC-REV-005 [P0]（V-REV-007/CV-REV-008 url 白名单）/ TC-REV-006 [P0]（rating/content 边界）/
 * TC-REV-009 [P0]（403801 四态，TradingPurchaseQueryPort 桩——跨 trading 领域服务接口调用，禁止直查 orders）/
 * TC-REV-021 单测面（404501 透传 bs-702）。
 */
@ExtendWith(MockitoExtension.class)
class StoreReviewServiceTest {

    private static final long USER = 77L;
    private static final long PRODUCT = 11L;
    private static final String BASE = "http://localhost:9000/dreamy-media";

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    ReviewImageRepository imageRepository;
    @Mock
    ReviewCacheService cache;
    @Mock
    TradingPurchaseQueryPort purchasePort;
    @Mock
    ReviewCatalogSnapshotPort catalogPort;
    @Mock
    ReviewIdentityQueryPort identityPort;

    StoreReviewService service;

    @BeforeEach
    void setUp() {
        service = new StoreReviewService(reviewRepository, imageRepository, cache, new ReviewImmediateTxRunner(),
                purchasePort, catalogPort, identityPort, new StorageProperties());
        lenient().when(catalogPort.getProductBrief(PRODUCT))
                .thenReturn(new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", true));
    }

    private StoreReviewCreate req(Integer rating, String content, List<ImageRef> images) {
        return new StoreReviewCreate(PRODUCT, rating, content, images);
    }

    // ==================== TC-REV-009 403801 四态 ====================

    @Test
    @DisplayName("TC-REV-009 [P0]: 无任何订单（端口 false）→ 403801 不落库")
    void noOrderRejected() {
        when(purchasePort.hasCompletedOrderContaining(USER, PRODUCT)).thenReturn(false);
        assertThatThrownBy(() -> service.createReview(USER, req(5, "great", null)))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_NOT_ALLOWED));
        verify(reviewRepository, never()).insert(any());
    }

    @Test
    @DisplayName("TC-REV-009 [P0]: paid 未 completed / completed 不含该商品（端口语义收口）→ 403801")
    void incompleteOrWrongProductRejected() {
        // 四态中「paid 未 completed」「completed 不含该商品」由 trading 端口 SQL 语义收口，
        // 本域断言：凡端口返回 false 一律 403801（fail-closed），不出现绕过路径
        when(purchasePort.hasCompletedOrderContaining(USER, PRODUCT)).thenReturn(false);
        assertThatThrownBy(() -> service.createReview(USER, req(4, null, null)))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_NOT_ALLOWED));
        verify(reviewRepository, never()).existsByUserAndProduct(anyLong(), anyLong());
    }

    @Test
    @DisplayName("TC-REV-009 [P0]: completed 含该商品 → 201 pending 落库（customer_name 快照 + featured=0）")
    void completedWithProductAccepted() {
        when(purchasePort.hasCompletedOrderContaining(USER, PRODUCT)).thenReturn(true);
        when(reviewRepository.existsByUserAndProduct(USER, PRODUCT)).thenReturn(false);
        when(identityPort.getUserName(USER)).thenReturn("Emma Johnson");
        StoreReviewDto dto = service.createReview(USER, req(5, "  lovely dress  ", null));
        assertThat(dto.status()).isEqualTo("pending");
        // MAP-REV-002 本人回执 customer_name 原样不脱敏
        assertThat(dto.customerName()).isEqualTo("Emma Johnson");
        verify(reviewRepository).insert(org.mockito.ArgumentMatchers.argThat((Review r) ->
                r.getStatus() == ReviewStatus.PENDING
                        && Boolean.FALSE.equals(r.getFeatured())
                        && r.getUserId() == USER
                        && "lovely dress".equals(r.getContent())
                        && r.getSubmittedAt() != null));
    }

    @Test
    @DisplayName("TC-REV-010 单测面 [P0]: 已评价过 → 409801（预检命中）")
    void alreadyReviewed() {
        when(purchasePort.hasCompletedOrderContaining(USER, PRODUCT)).thenReturn(true);
        when(reviewRepository.existsByUserAndProduct(USER, PRODUCT)).thenReturn(true);
        assertThatThrownBy(() -> service.createReview(USER, req(5, null, null)))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.ALREADY_REVIEWED));
    }

    @Test
    @DisplayName("TC-REV-021 单测面 [P0]: 商品不存在/draft → 404501 透传（bs-702）")
    void productMissingOrDraft() {
        when(catalogPort.getProductBrief(PRODUCT)).thenReturn(null);
        assertThatThrownBy(() -> service.createReview(USER, req(5, null, null)))
                .isInstanceOfSatisfying(CatalogException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));
        when(catalogPort.getProductBrief(PRODUCT))
                .thenReturn(new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", false));
        assertThatThrownBy(() -> service.createReview(USER, req(5, null, null)))
                .isInstanceOf(CatalogException.class);
    }

    // ==================== TC-REV-005/006 入参边界 ====================

    @Test
    @DisplayName("TC-REV-006 [P0]: rating 0/6/缺失拒绝 422801（bs-508/509），content 5001 超长拒绝")
    void ratingAndContentBoundaries() {
        for (Integer rating : new Integer[]{0, 6, null}) {
            assertThatThrownBy(() -> service.createReview(USER, req(rating, null, null)))
                    .isInstanceOfSatisfying(ReviewException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.FIELD_VALIDATION_FAILED));
        }
        assertThatThrownBy(() -> service.createReview(USER, req(5, "a".repeat(5001), null)))
                .isInstanceOfSatisfying(ReviewException.class, ex ->
                        assertThat(fields(ex)).containsEntry("content", "too_long"));
    }

    @Test
    @DisplayName("TC-REV-005 [P0]: images url 白名单——review/ 前缀通过；外链/product/ 前缀拒绝；>9 张拒绝")
    void imageUrlWhitelist() {
        when(purchasePort.hasCompletedOrderContaining(USER, PRODUCT)).thenReturn(true);
        when(reviewRepository.existsByUserAndProduct(USER, PRODUCT)).thenReturn(false);
        // 合法：本站 public_url + review/ 段
        StoreReviewDto ok = service.createReview(USER, req(5, null,
                List.of(new ImageRef(BASE + "/review/123/a.jpg"))));
        assertThat(ok.images()).hasSize(1);
        // 外链拒绝
        assertThatThrownBy(() -> service.createReview(USER, req(5, null,
                List.of(new ImageRef("https://evil.example.com/review/a.jpg")))))
                .isInstanceOfSatisfying(ReviewException.class, ex ->
                        assertThat(fields(ex)).containsEntry("images", "invalid_url"));
        // 其他 scope 前缀（product/）拒绝
        assertThatThrownBy(() -> service.createReview(USER, req(5, null,
                List.of(new ImageRef(BASE + "/product/123/a.jpg")))))
                .isInstanceOfSatisfying(ReviewException.class, ex ->
                        assertThat(fields(ex)).containsEntry("images", "invalid_url"));
        // >9 张拒绝
        List<ImageRef> ten = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new ImageRef(BASE + "/review/1/" + i + ".jpg")).toList();
        assertThatThrownBy(() -> service.createReview(USER, req(5, null, ten)))
                .isInstanceOfSatisfying(ReviewException.class, ex ->
                        assertThat(fields(ex)).containsEntry("images", "too_many"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fields(ReviewException ex) {
        assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.FIELD_VALIDATION_FAILED);
        return (Map<String, String>) ex.getDetails().get("fields");
    }
}
