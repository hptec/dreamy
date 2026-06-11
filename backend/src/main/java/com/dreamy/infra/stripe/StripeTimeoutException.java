package com.dreamy.infra.stripe;

/**
 * Stripe 超时（504 `504601 STRIPE_TIMEOUT`，error-strategy trading 码表）。
 * 降级语义同 502601：订单保持 pending 可重试（BE-DIM-5 降级矩阵）。
 */
public class StripeTimeoutException extends StripeException {

    public static final int CODE = 504601;

    public StripeTimeoutException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
