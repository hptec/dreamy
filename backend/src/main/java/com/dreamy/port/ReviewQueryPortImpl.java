package com.dreamy.port;

import com.dreamy.port.ReviewQueryPort;
import com.dreamy.domain.review.repository.ReviewRepository;
import org.springframework.stereotype.Component;

/**
 * ReviewQueryPort 真实实现（本域提供、catalog EVT-CAT-002 消费者回查，review-data-detail §8.2）。
 * RM-REV-002 同源聚合（avg 2 位 HALF_UP；零评价 0/0），保证 rating 回写口径与 E-REV-01 聚合展示强一致
 * （TC-REV-023）。catalog CatalogPortConfig 的 stub（@ConditionalOnMissingBean）自动让位。
 */
@Component
public class ReviewQueryPortImpl implements ReviewQueryPort {

    private final ReviewRepository reviewRepository;

    public ReviewQueryPortImpl(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public RatingSummary approvedRatingSummary(Long productId) {
        com.dreamy.support.RatingSummary summary =
                com.dreamy.support.RatingSummary.fromCounts(
                        reviewRepository.countApprovedByRating(productId));
        return new RatingSummary(summary.avg(), summary.count());
    }
}
