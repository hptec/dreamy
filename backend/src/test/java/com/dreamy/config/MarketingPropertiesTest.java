package com.dreamy.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MarketingProperties 启动校验单测（2026-07-18：退订密钥 fail-fast，覆盖空/过短/非法 TTL）。 */
class MarketingPropertiesTest {

    @Test
    @DisplayName("密钥缺失/过短/纯空白 → 启动 fail-fast")
    void secretRequired() {
        MarketingProperties p = new MarketingProperties();
        assertThatThrownBy(p::validateUnsubscribeSecret).isInstanceOf(IllegalStateException.class);
        p.setUnsubscribeSecret("short");
        assertThatThrownBy(p::validateUnsubscribeSecret).isInstanceOf(IllegalStateException.class);
        // 33 字符纯空白（低熵）同样拒绝——对齐 JWT 密钥校验标准
        p.setUnsubscribeSecret(" ".repeat(33));
        assertThatThrownBy(p::validateUnsubscribeSecret).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("非法 TTL（0/负数/超 10 年上限）→ 启动 fail-fast")
    void ttlMustBePositive() {
        MarketingProperties p = new MarketingProperties();
        p.setUnsubscribeSecret("a".repeat(32));
        p.setUnsubscribeTokenTtlSeconds(0);
        assertThatThrownBy(p::validateUnsubscribeSecret).isInstanceOf(IllegalStateException.class);
        p.setUnsubscribeTokenTtlSeconds(-1);
        assertThatThrownBy(p::validateUnsubscribeSecret).isInstanceOf(IllegalStateException.class);
        p.setUnsubscribeTokenTtlSeconds(10L * 365 * 24 * 3600 + 1);
        assertThatThrownBy(p::validateUnsubscribeSecret).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("≥32 字符密钥 + 正 TTL → 校验通过")
    void validConfigPasses() {
        MarketingProperties p = new MarketingProperties();
        p.setUnsubscribeSecret("a".repeat(32));
        p.setUnsubscribeTokenTtlSeconds(2592000);
        assertThatCode(p::validateUnsubscribeSecret).doesNotThrowAnyException();
    }
}
