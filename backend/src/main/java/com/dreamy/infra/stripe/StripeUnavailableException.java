package com.dreamy.infra.stripe;

/**
 * Stripe 不可用（502 `502601 STRIPE_UNAVAILABLE`，error-strategy trading 码表）。
 * 降级：下单订单保持 pending 可重试；退款审核事务整体回滚（BE-DIM-5 降级矩阵）。
 */
public class StripeUnavailableException extends StripeException {

    public static final int CODE = 502601;

    public StripeUnavailableException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
