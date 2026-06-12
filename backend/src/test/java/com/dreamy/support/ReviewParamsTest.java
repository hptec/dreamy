package com.dreamy.support;

import com.dreamy.enums.QuestionVisibility;
import com.dreamy.enums.ReviewSort;
import com.dreamy.enums.ReviewStatus;
import com.dreamy.error.ReviewException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 查询参数解析/枚举边界单元测试。
 * L2 TRACE: TC-REV-003 [P1]（sort 枚举映射与缺省）/ TC-REV-006 [P0]（bs-510/511 枚举外拒绝）/
 * TC-REV-033（分页边界 page_size=100 通过/101 拒绝/page=0 拒绝）。
 */
class ReviewParamsTest {

    @Test
    @DisplayName("TC-REV-003 [P1]: sort 四枚举解析正确，缺省 featured_first，枚举外 422801")
    void sortParsing() {
        ReviewFieldErrors errors = new ReviewFieldErrors();
        assertThat(ReviewParams.parseSort(null, errors)).isEqualTo(ReviewSort.FEATURED_FIRST);
        assertThat(ReviewParams.parseSort("newest", errors)).isEqualTo(ReviewSort.NEWEST);
        assertThat(ReviewParams.parseSort("rating_desc", errors)).isEqualTo(ReviewSort.RATING_DESC);
        assertThat(ReviewParams.parseSort("rating_asc", errors)).isEqualTo(ReviewSort.RATING_ASC);
        assertThat(ReviewParams.parseSort("featured_first", errors)).isEqualTo(ReviewSort.FEATURED_FIRST);
        assertThat(errors.hasErrors()).isFalse();
        ReviewParams.parseSort("__invalid__", errors);
        assertThat(errors.fields()).containsEntry("sort", "invalid_enum");
    }

    @Test
    @DisplayName("TC-REV-033 [P0]: 分页边界——page_size=100 通过 / 101 拒绝 / page=0 拒绝 / 缺省 1·20")
    void pageBounds() {
        ReviewFieldErrors ok = new ReviewFieldErrors();
        assertThat(ReviewParams.parsePage(null, ok)).isEqualTo(1);
        assertThat(ReviewParams.parsePageSize(null, ok)).isEqualTo(20);
        assertThat(ReviewParams.parsePageSize(100, ok)).isEqualTo(100);
        assertThat(ok.hasErrors()).isFalse();

        ReviewFieldErrors bad = new ReviewFieldErrors();
        ReviewParams.parsePage(0, bad);
        ReviewParams.parsePageSize(101, bad);
        assertThat(bad.fields()).containsKeys("page", "page_size");
        assertThatThrownBy(bad::throwIfAny).isInstanceOf(ReviewException.class);
    }

    @Test
    @DisplayName("V-REV-001 [P0]: product_id 必填正整数（缺=required / 非正=invalid）")
    void productIdRequired() {
        ReviewFieldErrors missing = new ReviewFieldErrors();
        ReviewParams.parseRequiredProductId(null, missing);
        assertThat(missing.fields()).containsEntry("product_id", "required");

        ReviewFieldErrors invalid = new ReviewFieldErrors();
        ReviewParams.parseRequiredProductId(0L, invalid);
        assertThat(invalid.fields()).containsEntry("product_id", "invalid");
    }

    @Test
    @DisplayName("TC-REV-006 [P0]: status/visible 枚举外拒绝（bs-510/511），合法值解析正确")
    void enumBoundaries() {
        assertThat(ReviewStatus.of("approved")).isEqualTo(ReviewStatus.APPROVED);
        assertThat(ReviewStatus.of("rejected")).isEqualTo(ReviewStatus.REJECTED);
        assertThat(ReviewStatus.of("pending")).isEqualTo(ReviewStatus.PENDING);
        assertThat(ReviewStatus.of("__invalid__")).isNull();
        assertThat(QuestionVisibility.of("visible")).isEqualTo(QuestionVisibility.VISIBLE);
        assertThat(QuestionVisibility.of("hidden")).isEqualTo(QuestionVisibility.HIDDEN);
        assertThat(QuestionVisibility.of("__invalid__")).isNull();
    }

    @Test
    @DisplayName("V-REV-019: search 长度 80 通过 / 81 拒绝 / trim 空视为未提供")
    void searchBounds() {
        ReviewFieldErrors errors = new ReviewFieldErrors();
        assertThat(ReviewParams.parseSearch("  ", errors)).isNull();
        assertThat(ReviewParams.parseSearch("a".repeat(80), errors)).hasSize(80);
        assertThat(errors.hasErrors()).isFalse();
        ReviewParams.parseSearch("a".repeat(81), errors);
        assertThat(errors.fields()).containsEntry("search", "too_long");
    }
}
