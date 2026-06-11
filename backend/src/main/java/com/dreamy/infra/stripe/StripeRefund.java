package com.dreamy.infra.stripe;

/**
 * Refund 投影（决策 25：原币种原金额沙箱退款）。
 * stripe_refund_id 可落库可入日志（非敏感引用，error-strategy 脱敏规则）。
 */
public record StripeRefund(
        String id,
        String status,
        long amountMinor,
        String currency
) {
}
