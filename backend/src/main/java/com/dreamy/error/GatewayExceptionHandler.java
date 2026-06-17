package com.dreamy.error;

import com.dreamy.controller.AdminAiController;
import com.dreamy.controller.AdminGatewayController;
import huihao.web.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * 网关 / AI 翻译域异常处理器（assignableTypes 限定本域 2 个 Controller）。
 * error-strategy R 包络 {code,message,data=details}；
 * 422 字段级结构 { fields: { <field>: <reason> } }；4xx WARN / 5xx ERROR 分级。
 * 决策8：admin 端错误 message 固定中文，不走 i18n 解析。
 */
@RestControllerAdvice(assignableTypes = {
        AdminGatewayController.class,
        AdminAiController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /** 域业务异常 → 6 位码映射。 */
    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<R<Object>> handleGateway(GatewayException ex, HttpServletRequest req) {
        GatewayErrorCode code = ex.getErrorCode();
        if (code.getHttpStatus() >= 500) {
            log.error("[GATEWAY] {} code={} details={}", reqLine(req), code.getCode(), ex.getDetails(), ex);
        } else {
            log.warn("[GATEWAY] {} code={} details={}", reqLine(req), code.getCode(), ex.getDetails());
        }
        return ResponseEntity.status(code.getHttpStatus())
                .body(new R<>(code.getCode(), code.getMessage(), ex.getDetails()));
    }

    /** Bean Validation 字段校验失败 → 422 + fields 字典。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException ex,
                                                       HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        }
        log.warn("[GATEWAY-VALIDATION] {} fields={}", reqLine(req), fields);
        return build(GatewayErrorCode.GATEWAY_VALIDATION, Map.of("fields", fields));
    }

    /** 请求体不可读（JSON 解析失败）→ 422。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Object>> handleUnreadable(HttpMessageNotReadableException ex,
                                                      HttpServletRequest req) {
        log.warn("[GATEWAY-VALIDATION] {} unreadable body", reqLine(req));
        return build(GatewayErrorCode.GATEWAY_VALIDATION, Map.of("fields", Map.of("_body", "malformed")));
    }

    /** 查询参数类型不匹配 → 422 fields.{param}=invalid_type。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                        HttpServletRequest req) {
        log.warn("[GATEWAY-VALIDATION] {} param={} type mismatch", reqLine(req), ex.getName());
        return build(GatewayErrorCode.GATEWAY_VALIDATION,
                Map.of("fields", Map.of(ex.getName(), "invalid_type")));
    }

    private ResponseEntity<R<Object>> build(GatewayErrorCode code, Map<String, Object> details) {
        return ResponseEntity.status(code.getHttpStatus())
                .body(new R<>(code.getCode(), code.getMessage(), details));
    }

    private String reqLine(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String qs = req.getQueryString();
        return req.getMethod() + " " + req.getRequestURI() + (qs != null ? "?" + qs : "");
    }
}
