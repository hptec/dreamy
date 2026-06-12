package com.dreamy.error;

/**
 * GA4 不可用（502001）。仅 DEC-ANA-5 ⑤ 触达：GA4 失败且读 stale/构造降级体过程中
 * 缓存基础设施再抛异常，由 AnalyticsExceptionHandler 映射 HTTP 502。
 */
public class Ga4UnavailableException extends AnalyticsException {

    public Ga4UnavailableException() {
        super(AnalyticsErrorCode.GA4_UNAVAILABLE);
    }
}
