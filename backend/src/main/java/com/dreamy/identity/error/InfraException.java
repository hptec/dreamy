package com.dreamy.identity.error;

/**
 * 基础设施异常（EX-26~29）。包装底层 cause，禁止向上泄漏堆栈（PATH-02）。
 * 由 GlobalExceptionHandler 统一映射为 5xx，响应不含细节（redaction.rule_5xx）。
 */
public class InfraException extends BizException {

    public InfraException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        if (cause != null) {
            initCause(cause);
        }
    }

    public InfraException(ErrorCode errorCode) {
        super(errorCode);
    }
}
