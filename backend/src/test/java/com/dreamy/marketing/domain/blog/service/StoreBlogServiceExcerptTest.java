package com.dreamy.marketing.domain.blog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * blog excerpt 派生单元测试（MAP-MKT-003：EN content strip 标记后截断 200）。
 * L2 TRACE: TC-MKT-007。
 */
class StoreBlogServiceExcerptTest {

    @Test
    @DisplayName("TC-MKT-007 [P1]: strip 标记 + 空白归一 + 截断 200")
    void deriveExcerpt() {
        assertThat(StoreBlogService.deriveExcerpt("<p>Hello <b>world</b></p>\n\nSecond  line"))
                .isEqualTo("Hello world Second line");
        String longText = "a".repeat(250);
        assertThat(StoreBlogService.deriveExcerpt(longText)).hasSize(200);
        assertThat(StoreBlogService.deriveExcerpt(null)).isNull();
        assertThat(StoreBlogService.deriveExcerpt("   ")).isNull();
    }
}
