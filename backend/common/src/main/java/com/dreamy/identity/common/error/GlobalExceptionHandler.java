package com.dreamy.identity.common.error;

import com.dreamy.identity.common.dto.ErrorBody;
import com.dreamy.identity.common.i18n.MessageResolver;
import com.dreamy.identity.common.i18n.RequestLocaleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器。统一输出 {code,message,details}。
 * 约束: PATH-01（领域/应用异常透传映射）、PATH-02（基础设施异常不泄漏堆栈）、PATH-04（兜底 50000）；
 * redaction.rule_5xx（5xx 不暴露 SQL/堆栈）；i18n（按 RequestLocaleContext 本地化）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageResolver messageResolver;

    public GlobalExceptionHandler(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    /** 基础设施异常（EX-26~29）：记 ERROR + 告警，响应不含细节（PATH-02） */
    @ExceptionHandler(InfraException.class)
    public ResponseEntity<ErrorBody> handleInfra(InfraException ex) {
        log.error("[INFRA] code={} ", ex.getErrorCode().getCode(), ex);
        return build(ex.getErrorCode(), ex.getDetails());
    }

    /** 业务异常（EX-01~25）：4xx WARN（LOG-02），透传映射 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ErrorBody> handleBiz(BizException ex) {
        log.warn("[BIZ] code={} ", ex.getErrorCode().getCode());
        return build(ex.getErrorCode(), ex.getDetails());
    }

    /** Bean Validation 字段校验失败（EX-02）→ 40000 VALIDATION_ERROR + 字段级 details */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[VALIDATION] fields={}", details.keySet());
        return build(ErrorCode.VALIDATION_ERROR, details);
    }

    /** 兜底：未预期异常（PATH-04 EX-30）→ 50000 INTERNAL_ERROR，不暴露细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception ex) {
        log.error("[INTERNAL] unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ErrorBody> build(ErrorCode code, Map<String, Object> details) {
        String message = messageResolver.resolve(code, RequestLocaleContext.get());
        ErrorBody body = new ErrorBody(code.getCode(), message, details);
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }
}
