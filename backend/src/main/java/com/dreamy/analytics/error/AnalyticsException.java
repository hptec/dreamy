package com.dreamy.analytics.error;

import lombok.Getter;

import java.util.Map;

/**
 * analytics 域业务异常。携带 AnalyticsErrorCode + 可选 details
 * （422001 {field:"range", allowed:[...]}，线上装入 R.data）。
 */
@Getter
public class AnalyticsException extends RuntimeException {

    private final AnalyticsErrorCode errorCode;
    private final transient Map<String, Object> details;

    public AnalyticsException(AnalyticsErrorCode errorCode) {
        this(errorCode, null);
    }

    public AnalyticsException(AnalyticsErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }
}
