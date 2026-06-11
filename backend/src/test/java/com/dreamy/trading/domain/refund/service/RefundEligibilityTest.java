package com.dreamy.trading.domain.refund.service;

import com.dreamy.trading.domain.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款资格判定纯函数单测（决策 24）。
 * L2 TRACE: TC-TRD-004 [P0]（宽限边界：now=deadline 可退、deadline+1s 不可退；未支付定制可退；
 * grace 取实时配置值）/ TC-TRD-005 [P1]（含已投产定制行 → refund_eligible=false）。
 */
class RefundEligibilityTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 6, 10, 10, 0, 0);

    @Test
    @DisplayName("TC-TRD-004 [P0]: now == paid_at+grace 边界可退；deadline+1s 不可退")
    void graceBoundary() {
        LocalDateTime deadline = PAID_AT.plusHours(24);
        assertThat(RefundEligibility.customProduced(PAID_AT, 24, deadline)).isFalse();
        assertThat(RefundEligibility.customProduced(PAID_AT, 24, deadline.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("TC-TRD-004 [P0]: 未支付订单定制行可退（paid_at == null）")
    void unpaidCustomRefundable() {
        assertThat(RefundEligibility.customProduced(null, 24, LocalDateTime.now())).isFalse();
        assertThat(RefundEligibility.lineRefundable(true, OrderStatus.PENDING, null, 24,
                LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("TC-TRD-004 [P0]: grace_hours 取 CheckoutConfig 实时值（48h 配置下 25h 仍可退）")
    void graceHoursConfigurable() {
        LocalDateTime now = PAID_AT.plusHours(25);
        assertThat(RefundEligibility.customProduced(PAID_AT, 24, now)).isTrue();
        assertThat(RefundEligibility.customProduced(PAID_AT, 48, now)).isFalse();
    }

    @Test
    @DisplayName("决策 24: 现货行 refundable = status ∈ {paid, shipped}")
    void spotLineByStatus() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(RefundEligibility.lineRefundable(false, OrderStatus.PAID, PAID_AT, 24, now)).isTrue();
        assertThat(RefundEligibility.lineRefundable(false, OrderStatus.SHIPPED, PAID_AT, 24, now)).isTrue();
        assertThat(RefundEligibility.lineRefundable(false, OrderStatus.PENDING, null, 24, now)).isFalse();
        assertThat(RefundEligibility.lineRefundable(false, OrderStatus.COMPLETED, PAID_AT, 24, now)).isFalse();
    }

    @Test
    @DisplayName("TC-TRD-005 [P1]: 含已投产定制行 → 整单 refund_eligible=false（reason_code=422602 由装配层派生）")
    void orderEligibilityWithProducedCustomLine() {
        LocalDateTime now = PAID_AT.plusHours(25);
        assertThat(RefundEligibility.orderEligible(OrderStatus.PAID, false, true, PAID_AT, 24, now)).isFalse();
        // 宽限期内可退
        assertThat(RefundEligibility.orderEligible(OrderStatus.PAID, false, true, PAID_AT, 24,
                PAID_AT.plusHours(23))).isTrue();
        // 进行中工单阻断
        assertThat(RefundEligibility.orderEligible(OrderStatus.PAID, true, false, PAID_AT, 24, now)).isFalse();
        // 状态阻断
        assertThat(RefundEligibility.orderEligible(OrderStatus.PENDING, false, false, null, 24, now)).isFalse();
        assertThat(RefundEligibility.orderEligible(OrderStatus.SHIPPED, false, false, PAID_AT, 24, now)).isTrue();
    }
}
