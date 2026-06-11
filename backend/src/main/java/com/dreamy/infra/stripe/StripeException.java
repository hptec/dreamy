package com.dreamy.infra.stripe;

import lombok.Getter;

/**
 * Stripe 基础设施异常基类。携带 trading 域码（error-strategy trading 域段 6）：
 * 502601 STRIPE_UNAVAILABLE / 504601 STRIPE_TIMEOUT。
 * 不携带 Stripe 错误负载细节（PATH-02 不泄漏堆栈/密钥，5xx 响应不含细节）。
 */
@Getter
public abstract class StripeException extends RuntimeException {

    /** trading 域 6 位错误码（502601/504601） */
    private final int code;

    protected StripeException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
