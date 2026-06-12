package com.dreamy.error;

/**
 * GA4 超时（504001）。仅 DEC-ANA-5 ⑤ 触达（timeout 失败形态 + 兜底链自身失效），
 * 由 AnalyticsExceptionHandler 映射 HTTP 504。
 */
public class Ga4TimeoutException extends AnalyticsException {

    public Ga4TimeoutException() {
        super(AnalyticsErrorCode.GA4_TIMEOUT);
    }
}
