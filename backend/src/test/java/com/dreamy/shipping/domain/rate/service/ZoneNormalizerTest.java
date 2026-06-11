package com.dreamy.shipping.domain.rate.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-SHP-009：zone 规范化纯函数（DEC-SHP-1：trim + 连续空白折叠 + 忽略大小写比较，存储保留大小写）。
 */
class ZoneNormalizerTest {

    @Test
    @DisplayName("TC-SHP-009 规范化：trim + 连续空白折叠为单空格")
    void normalizeCollapsesWhitespace() {
        assertThat(ZoneNormalizer.normalize(" North  America /  FedEx International Priority "))
                .isEqualTo("North America / FedEx International Priority");
        assertThat(ZoneNormalizer.normalize("Europe")).isEqualTo("Europe");
        assertThat(ZoneNormalizer.normalize("  ")).isEmpty();
        assertThat(ZoneNormalizer.normalize(null)).isNull();
    }

    @Test
    @DisplayName("TC-SHP-009 唯一比较忽略大小写：'north america' 与 'North America' 判同")
    void sameZoneIgnoresCase() {
        assertThat(ZoneNormalizer.sameZone("north america", "North America")).isTrue();
        assertThat(ZoneNormalizer.sameZone(" Europe ", "europe")).isTrue();
        assertThat(ZoneNormalizer.sameZone("Europe", "Oceania")).isFalse();
    }

    @Test
    @DisplayName("规范化保留原始大小写（存储语义）")
    void normalizePreservesCase() {
        assertThat(ZoneNormalizer.normalize("ReSt Of WoRlD")).isEqualTo("ReSt Of WoRlD");
    }
}
