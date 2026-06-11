package com.dreamy.analytics.error;

import huihao.web.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * analytics 域异常处理器（仅作用于 com.dreamy.analytics 包内 Controller，优先于 identity GlobalExceptionHandler）。
 * 约束: error-strategy R 包络（失败 {code,message,data=details}）；admin 端固定中文；4xx WARN / 5xx ERROR 分级。
 * identity 复用码（40100/40300/50000/50001 BizException / DB 异常）仍由 identity GlobalExceptionHandler 兜底。
 */
@RestControllerAdvice(basePackages = "com.dreamy.analytics")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnalyticsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsExceptionHandler.class);

    /** analytics 域业务异常 → 6 位码映射（422001/502001/504001） */
    @ExceptionHandler(AnalyticsException.class)
    public ResponseEntity<R<Object>> handleAnalytics(AnalyticsException ex, HttpServletRequest req) {
        if (ex.getErrorCode().getHttpStatus() >= 500) {
            log.error("[ANALYTICS] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(),
                    ex.getDetails(), ex);
        } else {
            log.warn("[ANALYTICS] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(),
                    ex.getDetails());
        }
        return build(ex.getErrorCode(), ex.getDetails());
    }

    private String reqLine(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String qs = req.getQueryString();
        return req.getMethod() + " " + req.getRequestURI() + (qs != null ? "?" + qs : "");
    }

    /** R 包络：{code, message(中文), data=details}；HTTP 状态取码高 3 位 */
    private ResponseEntity<R<Object>> build(AnalyticsErrorCode code, Map<String, Object> details) {
        return ResponseEntity.status(code.getHttpStatus())
                .body(new R<>(code.getCode(), code.getMessageZh(), details));
    }
}
