package com.dreamy.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 金额纯函数单测。
 * L2 TRACE: TC-TRD-001 [P0]（恒等式 HALF_UP 2 位）/ TC-TRD-002 [P0]（覆盖价优先 + Stripe 最小单位）。
 */
class MoneyTest {

    @Test
    @DisplayName("TC-TRD-001 [P0]: total = subtotal + shipping + gift_wrap − discount（HALF_UP 2 位）")
    void amountIdentity() {
        BigDecimal total = Money.total(new BigDecimal("100.00"), new BigDecimal("25.00"),
                new BigDecimal("15.00"), new BigDecimal("10.00"));
        assertThat(total).isEqualByComparingTo("130.00");
        assertThat(total.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-TRD-001 [P0]: 恒等式负值违反 → IllegalStateException（服务端自算不可能违反，js_guard）")
    void negativeTotalRejected() {
        assertThatThrownBy(() -> Money.total(new BigDecimal("10.00"), Money.zero(), Money.zero(),
                new BigDecimal("20.00"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-TRD-002 [P0]: multi_currency_prices 覆盖价优先，否则 USD×rate HALF_UP")
    void coveredPricePriority() {
        Map<String, BigDecimal> covered = Map.of("CAD", new BigDecimal("99.00"));
        // 覆盖价命中
        assertThat(Money.unitPrice(new BigDecimal("100.00"), covered, "CAD", new BigDecimal("1.36")))
                .isEqualByComparingTo("99.00");
        // 无覆盖价 → USD×rate（100.005 → HALF_UP 136.01）
        assertThat(Money.unitPrice(new BigDecimal("100.005"), covered, "EUR", new BigDecimal("0.92")))
                .isEqualByComparingTo("92.00");
        assertThat(Money.unitPrice(new BigDecimal("100.00"), null, "GBP", new BigDecimal("0.795")))
                .isEqualByComparingTo("79.50");
    }

    @Test
    @DisplayName("TC-TRD-002 [P0]: Stripe 金额 = total×100 取整（五币种均 2 位小数币，决策 14）")
    void stripeMinorUnits() {
        assertThat(Money.toMinor(new BigDecimal("130.00"))).isEqualTo(13000L);
        assertThat(Money.toMinor(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(Money.toMinor(new BigDecimal("99.99"))).isEqualTo(9999L);
    }
}
