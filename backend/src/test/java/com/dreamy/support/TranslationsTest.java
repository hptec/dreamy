package com.dreamy.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 翻译回退合并单元测试（决策 13 逐字段覆盖/回退）。
 * L2 TRACE: TC-MKT-006（七类内容同函数）。
 */
class TranslationsTest {

    @Test
    @DisplayName("TC-MKT-006 [P0]: 译文非空白覆盖；null/空白回退 EN")
    void coalesce() {
        assertThat(Translations.coalesce("Vestido", "Gown")).isEqualTo("Vestido");
        assertThat(Translations.coalesce(null, "Gown")).isEqualTo("Gown");
        assertThat(Translations.coalesce("   ", "Gown")).isEqualTo("Gown");
        assertThat(Translations.coalesce(null, null)).isNull();
    }

    @Test
    @DisplayName("TC-MKT-006 [P0]: 仅 es/fr 查附表，EN 直读主表")
    void needsTranslation() {
        assertThat(Translations.needsTranslation("es")).isTrue();
        assertThat(Translations.needsTranslation("fr")).isTrue();
        assertThat(Translations.needsTranslation("en")).isFalse();
        assertThat(Translations.needsTranslation(null)).isFalse();
    }
}
