package com.dreamy.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三状态机 guard 矩阵单测（前置 js_guard 口径；终防线为条件更新 CAS——IT 层验证）。
 * L2 TRACE: TC-TRD-006 [P0]（order_lifecycle 9 转换 + 全部非法拒绝，TASK-038）/
 * TC-TRD-007 [P0]（payment_lifecycle，TASK-039）/ TC-TRD-008 [P0]（refund_lifecycle，TASK-041）。
 */
class StateMachineTest {

    @Test
    @DisplayName("TC-TRD-006 [P0]: order_lifecycle 9 合法转换全通过")
    void orderLegalTransitions() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    @DisplayName("TC-TRD-006 [P0]: 非法转换全部拒绝（409602 口径）——终态无出边/跨级跳转拒绝")
    void orderIllegalTransitionsRejected() {
        int legal = 0;
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                if (from.canTransitionTo(to)) {
                    legal++;
                }
            }
        }
        // 恰 9 条合法转换，其余全部 guard 拒绝
        assertThat(legal).isEqualTo(9);
        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.REFUNDING)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("TC-TRD-007 [P0]: payment_lifecycle——succeeded 收 failed 等非法事件拒绝；failed→created 重建")
    void paymentLifecycle() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.SUCCEEDED)).isTrue();
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.SUCCEEDED)).isTrue();
        assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
        // RM-TRD-044 retryOrderPayment 重建 PI：failed→created
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.CREATED)).isTrue();
        // 非法事件
        assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
        assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.SUCCEEDED)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.SUCCEEDED)).isFalse();
    }

    @Test
    @DisplayName("TC-TRD-008 [P0]: refund_lifecycle——仅 pending 可审；非 pending 审核拒绝（409604）")
    void refundLifecycle() {
        assertThat(RefundStatus.PENDING.canTransitionTo(RefundStatus.APPROVED)).isTrue();
        assertThat(RefundStatus.PENDING.canTransitionTo(RefundStatus.REJECTED)).isTrue();
        assertThat(RefundStatus.APPROVED.canTransitionTo(RefundStatus.REJECTED)).isFalse();
        assertThat(RefundStatus.REJECTED.canTransitionTo(RefundStatus.APPROVED)).isFalse();
        assertThat(RefundStatus.APPROVED.canTransitionTo(RefundStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("MAP-TRD-012: 契约字符串 ↔ 枚举双向（未知值 null → 422601）")
    void contractKeys() {
        assertThat(OrderStatus.of(6)).isEqualTo(OrderStatus.REFUNDING);
        assertThat(OrderStatus.of(99)).isNull();
        assertThat(PaymentStatus.of(3)).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(RefundStatus.of(3)).isEqualTo(RefundStatus.REJECTED);
    }
}
