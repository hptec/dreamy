package com.dreamy.identity.common.error;

import lombok.Getter;

import java.util.Map;

/**
 * 业务异常基类。携带 ErrorCode + 可选 details（字段级错误 / remaining_attempts / remaining_resend_seconds）。
 * 约束: PATH-01 领域/应用异常透传至 GlobalExceptionHandler 映射 {code,message,details}。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;

    public BizException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BizException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }
}
