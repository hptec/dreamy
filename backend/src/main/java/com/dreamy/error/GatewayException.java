package com.dreamy.error;

import lombok.Getter;

import java.util.Map;

/**
 * 网关/AI翻译/术语表领域业务异常。携带 GatewayErrorCode + 可选 details。
 * L2 TRACE: i18n-backend-error-mapping.yml；error-strategy 分层错误处理（应用层抛 → 表示层映射）。
 */
@Getter
public class GatewayException extends RuntimeException {

    private final GatewayErrorCode errorCode;
    private final transient Map<String, Object> details;

    public GatewayException(GatewayErrorCode errorCode) {
        this(errorCode, null);
    }

    public GatewayException(GatewayErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }

    public GatewayException(GatewayErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")", cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public static GatewayException fieldValidation(GatewayErrorCode code, Map<String, String> fields) {
        return new GatewayException(code, Map.of("fields", fields));
    }
}
