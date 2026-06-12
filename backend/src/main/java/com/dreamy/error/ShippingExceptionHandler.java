package com.dreamy.error;

import com.dreamy.controller.AdminCarrierController;
import com.dreamy.controller.AdminShippingRateController;

import huihao.web.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * shipping 域异常处理器（仅作用于 shipping 域 Controller（assignableTypes 枚举，包合并后替代 basePackages 隔离），优先于 identity GlobalExceptionHandler）。
 * 约束: error-strategy R 包络（失败 {code,message,data=details}）；admin 端固定中文；4xx WARN / 5xx ERROR 分级。
 * identity 复用码（40100/40300/50000/50001 BizException）与锁等待超时等未预期异常（→50000）
 * 仍由 identity GlobalExceptionHandler 兜底处理（EC-SHP-001 锁超时走通用码语义）。
 */
@RestControllerAdvice(assignableTypes = {AdminCarrierController.class, AdminShippingRateController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShippingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ShippingExceptionHandler.class);

    /** shipping 域业务异常 → 6 位码映射 */
    @ExceptionHandler(ShippingException.class)
    public ResponseEntity<R<Object>> handleShipping(ShippingException ex, HttpServletRequest req) {
        if (ex.getErrorCode().getHttpStatus() >= 500) {
            log.error("[SHIPPING] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails(), ex);
        } else {
            log.warn("[SHIPPING] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        }
        return build(ex.getErrorCode(), ex.getDetails());
    }

    /** 请求体不可读（JSON 解析失败/类型错位，如 fee 传非数值）→ 422901（V-SHP-010 非法 number 口径） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("[SHIPPING-VALIDATION] {} unreadable body", reqLine(req));
        return build(ShippingErrorCode.FIELD_VALIDATION_FAILED, Map.of("field", "_body"));
    }

    private String reqLine(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String qs = req.getQueryString();
        return req.getMethod() + " " + req.getRequestURI() + (qs != null ? "?" + qs : "");
    }

    /** R 包络：{code, message(中文), data=details}；HTTP 状态取码高 3 位 */
    private ResponseEntity<R<Object>> build(ShippingErrorCode code, Map<String, Object> details) {
        return ResponseEntity.status(code.getHttpStatus())
                .body(new R<>(code.getCode(), code.getMessageZh(), details));
    }
}
