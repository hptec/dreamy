package com.dreamy.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三状态机 guard 矩阵单测（前置 js_guard 口径；终防线为条件更新 CAS——IT 层验证）。
 * L2 TRACE: TC-TRD-006 [P0]（order_lifecycle 转换矩阵——order-flow-complete §2.4 扩为 8 态 14 边，TASK-038）/
 * TC-TRD-007 [P0]（payment_lifecycle，TASK-039）/ TC-TRD-008 [P0]（refund_lifecycle，TASK-041）。
 */
class StateMachineTest {

    /** order-flow-complete §2.4 转换矩阵（14 条合法边）全量 */
    private static final Map<OrderStatus, Set<OrderStatus>> EXPECTED = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.SHIPPED, OrderStatus.REFUNDING, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderStatus.REFUNDING),
            OrderStatus.DELIVERED, Set.of(OrderStatus.COMPLETED, OrderStatus.REFUNDING),
            OrderStatus.REFUNDING, Set.of(OrderStatus.REFUNDED, OrderStatus.PAID, OrderStatus.SHIPPED,
                    OrderStatus.DELIVERED),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.REFUNDED, Set.of());

    @Test
    @DisplayName("TC-TRD-006 [P0] / order-flow-complete §2.4: order_lifecycle 转换矩阵全量（8×8 逐格断言，恰 14 条合法）")
    void orderTransitionMatrixExhaustive() {
        int legal = 0;
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                boolean expected = EXPECTED.get(from).contains(to);
                assertThat(from.canTransitionTo(to))
                        .as("%s → %s", from, to)
                        .isEqualTo(expected);
                if (expected) {
                    legal++;
                }
            }
        }
        assertThat(legal).isEqualTo(14);
        // null 目标一律拒绝
        assertThat(OrderStatus.PENDING.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("order-flow-complete §2.4: 新增 DELIVERED(8) 边——shipped→delivered / delivered→completed|refunding / refunding→delivered")
    void deliveredEdges() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        // STATE-7：paid→cancelled 仅后台且须同事务全额退款（矩阵放行，语义由 RefundService 保证）
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("order-flow-complete: isTerminal / isPostPaymentActive 分类")
    void classification() {
        assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();
        assertThat(OrderStatus.DELIVERED.isTerminal()).isFalse();
        assertThat(OrderStatus.REFUNDING.isTerminal()).isFalse();
        for (OrderStatus s : OrderStatus.values()) {
            boolean active = s == OrderStatus.PAID || s == OrderStatus.SHIPPED || s == OrderStatus.DELIVERED;
            assertThat(s.isPostPaymentActive()).as("%s", s).isEqualTo(active);
        }
    }

    @Test
    @DisplayName("TC-TRD-006 [P0]: 非法转换全部拒绝（409602 口径）——终态无出边/跨级跳转拒绝")
    void orderIllegalTransitionsRejected() {
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
        // order-flow-complete：部分退款累计
        assertThat(PaymentStatus.SUCCEEDED.canTransitionTo(PaymentStatus.PARTIALLY_REFUNDED)).isTrue();
        assertThat(PaymentStatus.PARTIALLY_REFUNDED.canTransitionTo(PaymentStatus.PARTIALLY_REFUNDED)).isTrue();
        assertThat(PaymentStatus.PARTIALLY_REFUNDED.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
        assertThat(PaymentStatus.PARTIALLY_REFUNDED.canTransitionTo(PaymentStatus.SUCCEEDED)).isFalse();
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
