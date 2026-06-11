package com.dreamy.infra.stripe;

/**
 * PaymentIntent 投影（基建只暴露七域所需最小字段）。
 * client_secret 不落库（即取即用，error-strategy 脱敏规则）；amountMinor 为最小货币单位
 * （= total_amount × 100 取整，五币种均为 2 位小数币，决策 14/15）。
 */
public record StripePaymentIntent(
        String id,
        String clientSecret,
        String status,
        long amountMinor,
        String currency
) {
}
