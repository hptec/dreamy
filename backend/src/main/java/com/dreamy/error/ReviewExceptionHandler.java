package com.dreamy.error;

import com.dreamy.controller.AdminQuestionController;
import com.dreamy.controller.AdminReviewController;
import com.dreamy.controller.StoreReviewController;
import com.dreamy.controller.StoreUploadController;

import com.dreamy.error.CatalogException;
import com.dreamy.i18n.CatalogMessageResolver;
import com.dreamy.i18n.RequestLocaleContext;
import com.dreamy.i18n.ReviewMessageResolver;
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
 * review 域异常处理器（仅作用于 review 域 Controller（assignableTypes 枚举，包合并后替代 basePackages 隔离），优先于 identity GlobalExceptionHandler）。
 * 约束: error-strategy R 包络（失败 {code,message,data=details}）；422 字段级结构
 * `{ fields: { <field>: <reason_key> } }`（review-api-detail §0 横切）；4xx WARN / 5xx ERROR 分级。
 * 商品引用校验透传 catalog 码（404501 等 CatalogException，trading V-TRD-002 同口径先例）；
 * identity 复用码（40100/40300/50000 等 BizException）仍由 identity GlobalExceptionHandler 兜底。
 */
@RestControllerAdvice(assignableTypes = {AdminQuestionController.class, AdminReviewController.class, StoreReviewController.class, StoreUploadController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReviewExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewExceptionHandler.class);

    private final ReviewMessageResolver messageResolver;
    private final CatalogMessageResolver catalogMessageResolver;

    public ReviewExceptionHandler(ReviewMessageResolver messageResolver,
                                  CatalogMessageResolver catalogMessageResolver) {
        this.messageResolver = messageResolver;
        this.catalogMessageResolver = catalogMessageResolver;
    }

    /** review 域业务异常 → 6 位码映射 */
    @ExceptionHandler(ReviewException.class)
    public ResponseEntity<R<Object>> handleReview(ReviewException ex, HttpServletRequest req) {
        if (ex.getErrorCode().getHttpStatus() >= 500) {
            log.error("[REVIEW] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails(), ex);
        } else {
            log.warn("[REVIEW] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        }
        String message = messageResolver.resolve(ex.getErrorCode(), RequestLocaleContext.get());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(new R<>(ex.getErrorCode().getCode(), message, ex.getDetails()));
    }

    /** catalog 域异常透传（V-REV-004/010 商品存在性校验 → 404501，trading 已有先例） */
    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<R<Object>> handleCatalogPassthrough(CatalogException ex, HttpServletRequest req) {
        log.warn("[REVIEW] {} passthrough catalog code={}", reqLine(req), ex.getErrorCode().getCode());
        String message = catalogMessageResolver.resolve(ex.getErrorCode(), RequestLocaleContext.get());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(new R<>(ex.getErrorCode().getCode(), message, ex.getDetails()));
    }

    /** Bean Validation 字段校验失败 → 422801 + fields 字典（error-strategy L2 要求 1） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        }
        log.warn("[REVIEW-VALIDATION] {} fields={}", reqLine(req), fields);
        return build422(Map.of("fields", fields));
    }

    /** 请求体不可读（JSON 解析失败/类型错位）→ 422801 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("[REVIEW-VALIDATION] {} unreadable body", reqLine(req));
        return build422(Map.of("fields", Map.of("_body", "malformed")));
    }

    /** 查询参数类型不匹配 → 422801 fields.{param}=invalid_type（V-REV-001 非法 int64 口径） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                        HttpServletRequest req) {
        log.warn("[REVIEW-VALIDATION] {} param={} type mismatch", reqLine(req), ex.getName());
        return build422(Map.of("fields", Map.of(ex.getName(), "invalid_type")));
    }

    private ResponseEntity<R<Object>> build422(Map<String, Object> details) {
        String message = messageResolver.resolve(ReviewErrorCode.FIELD_VALIDATION_FAILED, RequestLocaleContext.get());
        return ResponseEntity.status(422)
                .body(new R<>(ReviewErrorCode.FIELD_VALIDATION_FAILED.getCode(), message, details));
    }

    private String reqLine(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String qs = req.getQueryString();
        return req.getMethod() + " " + req.getRequestURI() + (qs != null ? "?" + qs : "");
    }
}
