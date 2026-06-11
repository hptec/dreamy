package com.dreamy.marketing.domain.banner.service;

import com.dreamy.marketing.domain.banner.entity.Banner;
import com.dreamy.marketing.domain.enums.ContentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * banner 投放窗口谓词单元测试（DEC-MKT-2 权威读口径）。
 * L2 TRACE: TC-MKT-008（start/end 空端开放；now<start 不出；now>end 不出；archived/draft 不出）。
 */
class StoreBannerWindowTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 10, 12, 0);

    private static Banner banner(ContentStatus status, LocalDateTime start, LocalDateTime end) {
        Banner b = new Banner();
        b.setStatus(status);
        b.setStartTime(start);
        b.setEndTime(end);
        return b;
    }

    @Test
    @DisplayName("TC-MKT-008 [P0]: 窗口内 published 出；空端开放")
    void inWindowCases() {
        assertThat(StoreBannerService.inWindow(
                banner(ContentStatus.PUBLISHED, NOW.minusDays(1), NOW.plusDays(1)), NOW)).isTrue();
        assertThat(StoreBannerService.inWindow(banner(ContentStatus.PUBLISHED, null, null), NOW)).isTrue();
        assertThat(StoreBannerService.inWindow(banner(ContentStatus.PUBLISHED, null, NOW.plusDays(1)), NOW)).isTrue();
        assertThat(StoreBannerService.inWindow(banner(ContentStatus.PUBLISHED, NOW.minusDays(1), null), NOW)).isTrue();
        // 边界：start=now 进入；end=now 移出（end_time>now 谓词）
        assertThat(StoreBannerService.inWindow(banner(ContentStatus.PUBLISHED, NOW, NOW.plusDays(1)), NOW)).isTrue();
        assertThat(StoreBannerService.inWindow(banner(ContentStatus.PUBLISHED, NOW.minusDays(1), NOW), NOW)).isFalse();
    }

    @Test
    @DisplayName("TC-MKT-008 [P0]: now<start 不出；now>end 不出（状态不变——DEC-MKT-2）；archived/draft 不出")
    void outOfWindowCases() {
        assertThat(StoreBannerService.inWindow(
                banner(ContentStatus.PUBLISHED, NOW.plusMinutes(1), NOW.plusDays(1)), NOW)).isFalse();
        assertThat(StoreBannerService.inWindow(
                banner(ContentStatus.PUBLISHED, NOW.minusDays(2), NOW.minusDays(1)), NOW)).isFalse();
        assertThat(StoreBannerService.inWindow(
                banner(ContentStatus.DRAFT, NOW.minusDays(1), NOW.plusDays(1)), NOW)).isFalse();
        assertThat(StoreBannerService.inWindow(
                banner(ContentStatus.ARCHIVED, NOW.minusDays(1), NOW.plusDays(1)), NOW)).isFalse();
        assertThat(StoreBannerService.inWindow(null, NOW)).isFalse();
    }
}
