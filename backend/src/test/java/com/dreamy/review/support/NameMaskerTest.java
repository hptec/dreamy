package com.dreamy.review.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 姓名脱敏规则单元测试（MAP-REV-001/004）。
 * L2 TRACE: TC-REV-001 [P1]。
 */
class NameMaskerTest {

    @Test
    @DisplayName("TC-REV-001 [P1]: 双段名 → 首段+末段首字母.（Madison Reyes → Madison R.）")
    void twoSegments() {
        assertThat(NameMasker.mask("Madison Reyes")).isEqualTo("Madison R.");
        assertThat(NameMasker.mask("Emma Johnson")).isEqualTo("Emma J.");
    }

    @Test
    @DisplayName("TC-REV-001 [P1]: 单段名原样")
    void singleSegment() {
        assertThat(NameMasker.mask("Madison")).isEqualTo("Madison");
    }

    @Test
    @DisplayName("TC-REV-001 [P1]: 空快照 → Guest（null/空串/全空白）")
    void blankSnapshot() {
        assertThat(NameMasker.mask(null)).isEqualTo("Guest");
        assertThat(NameMasker.mask("")).isEqualTo("Guest");
        assertThat(NameMasker.mask("   ")).isEqualTo("Guest");
    }

    @Test
    @DisplayName("TC-REV-001 [P1]: 多段取首段+末段首字母（中间段忽略）")
    void multiSegments() {
        assertThat(NameMasker.mask("Mary Jane Watson Parker")).isEqualTo("Mary P.");
        assertThat(NameMasker.mask("  Ana  de  Armas ")).isEqualTo("Ana A.");
    }
}
