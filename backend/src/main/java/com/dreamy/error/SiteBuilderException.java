package com.dreamy.error;

import lombok.Getter;

import java.util.Map;

/**
 * site_builder 域业务异常。携带 SiteBuilderErrorCode + 可选 details。
 */
@Getter
public class SiteBuilderException extends RuntimeException {

    private final SiteBuilderErrorCode errorCode;
    private final transient Map<String, Object> details;

    public SiteBuilderException(SiteBuilderErrorCode errorCode) {
        this(errorCode, null);
    }

    public SiteBuilderException(SiteBuilderErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }

    public static SiteBuilderException of(SiteBuilderErrorCode code) {
        return new SiteBuilderException(code);
    }

    public static SiteBuilderException of(SiteBuilderErrorCode code, Map<String, Object> details) {
        return new SiteBuilderException(code, details);
    }
}
