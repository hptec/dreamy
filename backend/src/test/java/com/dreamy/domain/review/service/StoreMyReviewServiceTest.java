package com.dreamy.domain.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.infra.storage.StorageProperties;
import com.dreamy.enums.ReviewStatus;
import com.dreamy.domain.review.entity.Review;
import com.dreamy.domain.review.entity.ReviewImage;
import com.dreamy.domain.review.repository.ReviewImageRepository;
import com.dreamy.domain.review.repository.ReviewRepository;
import com.dreamy.dto.ReviewDtos.StoreMyReviewDto;
import com.dreamy.error.ReviewException;
import com.dreamy.infra.ReviewCacheService;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.port.ReviewCatalogSnapshotPort.ProductBrief;
import com.dreamy.port.ReviewIdentityQueryPort;
import com.dreamy.port.TradingPurchaseQueryPort;
import com.dreamy.testsupport.ReviewImmediateTxRunner;
import huihao.page.Paginated;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-REV-16 我的评价单元测试（F-049，L3 修复轮新增）。
 * 断言面：本人过滤（RM-REV-015 按 JWT customer_id，全状态可见）/ Paginated 六字段分页装配 /
 * images 全量含 rejected（本人视角）/ product 卡片简况批量装配（商品已删除容忍 null）/ 入参校验 422801。
 */
@ExtendWith(MockitoExtension.class)
class StoreMyReviewServiceTest {

    private static final long USER = 77L;

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
    }

    private Review review(long id, long productId, ReviewStatus status) {
        Review r = new Review();
        r.setId(id);
        r.setProductId(productId);
        r.setUserId(USER);
        r.setCustomerName("Emma Johnson");
        r.setRating(5);
        r.setContent("lovely");
        r.setStatus(status);
        r.setFeatured(false);
        r.setSubmittedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        return r;
    }

    private Page<Review> page(int page, int size, long total, List<Review> records) {
        Page<Review> p = new Page<>(page, size);
        p.setTotal(total);
        p.setRecords(records);
        return p;
    }

    @Test
    @DisplayName("本人过滤：按 JWT customer_id 走 RM-REV-015，pending/rejected 全状态对本人可见")
    void filtersByOwnerAndShowsAllStatuses() {
        when(reviewRepository.pageByUser(USER, 1, 20)).thenReturn(page(1, 20, 2,
                List.of(review(1L, 11L, ReviewStatus.PENDING), review(2L, 12L, ReviewStatus.REJECTED))));
        when(imageRepository.listByReviewIds(anyCollection(), eq(false))).thenReturn(List.of());
        when(catalogPort.getProductBriefs(anyCollection())).thenReturn(Map.of(
                11L, new ProductBrief(11L, "aurelia-gown", "Aurelia Gown", true)));

        Paginated<StoreMyReviewDto> result = service.listMyReviews(USER, null, null);

        verify(reviewRepository).pageByUser(USER, 1, 20);
        assertThat(result.getData()).extracting(StoreMyReviewDto::status)
                .containsExactly(1, 3);
        // product 卡片：命中简况 / 商品已删除容忍（仅 id，slug/name=null）
        assertThat(result.getData().get(0).product().name()).isEqualTo("Aurelia Gown");
        assertThat(result.getData().get(1).product().id()).isEqualTo(12L);
        assertThat(result.getData().get(1).product().name()).isNull();
    }

    @Test
    @DisplayName("分页装配：Paginated 六字段（total/page/page_size/number_of_elements/total_pages）")
    void paginationAssembly() {
        when(reviewRepository.pageByUser(USER, 2, 10)).thenReturn(page(2, 10, 31,
                List.of(review(1L, 11L, ReviewStatus.APPROVED))));
        when(imageRepository.listByReviewIds(anyCollection(), eq(false))).thenReturn(List.of());
        when(catalogPort.getProductBriefs(anyCollection())).thenReturn(Map.of());

        Paginated<StoreMyReviewDto> result = service.listMyReviews(USER, 2, 10);

        assertThat(result.getTotalElements()).isEqualTo(31);
        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getNumberOfElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(4);
    }

    @Test
    @DisplayName("images 本人视角全量（excludeRejected=false，含 rejected 标记——与 MAP-REV-002 同口径）")
    void imagesIncludeRejectedForOwner() {
        when(reviewRepository.pageByUser(USER, 1, 20)).thenReturn(page(1, 20, 1,
                List.of(review(1L, 11L, ReviewStatus.APPROVED))));
        ReviewImage rejected = new ReviewImage();
        rejected.setId(9L);
        rejected.setReviewId(1L);
        rejected.setUrl("http://localhost:9000/dreamy-media/review/x.jpg");
        rejected.setRejected(true);
        when(imageRepository.listByReviewIds(anyCollection(), eq(false))).thenReturn(List.of(rejected));
        when(catalogPort.getProductBriefs(anyCollection())).thenReturn(Map.of());

        Paginated<StoreMyReviewDto> result = service.listMyReviews(USER, null, null);

        assertThat(result.getData().get(0).images()).hasSize(1);
        assertThat(result.getData().get(0).images().get(0).rejected()).isTrue();
    }

    @Test
    @DisplayName("入参校验：page/page_size 越界 → 422801（ReviewFieldErrors 口径），不触达仓储")
    void invalidPagingRejected() {
        assertThatThrownBy(() -> service.listMyReviews(USER, 0, 200))
                .isInstanceOf(ReviewException.class);
        verify(reviewRepository, never()).pageByUser(anyLong(), anyInt(), anyInt());
    }
}
