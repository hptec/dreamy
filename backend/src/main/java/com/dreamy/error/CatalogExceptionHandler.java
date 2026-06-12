package com.dreamy.error;

import com.dreamy.controller.AdminAttributeController;
import com.dreamy.controller.AdminCategoryController;
import com.dreamy.controller.AdminProductController;
import com.dreamy.controller.AdminTagController;
import com.dreamy.controller.AdminUploadController;
import com.dreamy.controller.StoreCategoryController;
import com.dreamy.controller.StoreProductController;

import com.dreamy.i18n.CatalogMessageResolver;
import com.dreamy.i18n.RequestLocaleContext;
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
 * catalog 域异常处理器（仅作用于 catalog 域 Controller（assignableTypes 枚举，包合并后替代 basePackages 隔离），优先于 identity GlobalExceptionHandler）。
 * 约束: error-strategy R 包络（失败 {code,message,data=details}）；422 字段级结构
 * `{ fields: { <field>: <reason_key> } }`（api-detail §0 横切）；4xx WARN / 5xx ERROR 分级。
 * identity 复用码（40100/40300/50000 等 BizException）仍由 identity GlobalExceptionHandler 兜底处理。
 */
@RestControllerAdvice(assignableTypes = {AdminAttributeController.class, AdminCategoryController.class, AdminProductController.class, AdminTagController.class, AdminUploadController.class, StoreCategoryController.class, StoreProductController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogExceptionHandler.class);

    private final CatalogMessageResolver messageResolver;

    public CatalogExceptionHandler(CatalogMessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    /** catalog 域业务异常 → 6 位码映射 */
    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<R<Object>> handleCatalog(CatalogException ex, HttpServletRequest req) {
        if (ex.getErrorCode().getHttpStatus() >= 500) {
            log.error("[CATALOG] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails(), ex);
        } else {
            log.warn("[CATALOG] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        }
        return build(ex.getErrorCode(), ex.getDetails());
    }

    /** Bean Validation 字段校验失败 → 422501 + fields 字典（error-strategy L2 要求 1） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        }
        log.warn("[CATALOG-VALIDATION] {} fields={}", reqLine(req), fields);
        return build(CatalogErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", fields));
    }

    /** 请求体不可读（JSON 解析失败/类型错位）→ 422501（V-CAT-014 非数值等场景） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("[CATALOG-VALIDATION] {} unreadable body", reqLine(req));
        return build(CatalogErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", Map.of("_body", "malformed")));
    }

    /** 查询参数类型不匹配 → 422501 fields.{param}=invalid_type（V-CAT-005/019 非法 int64 口径） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        log.warn("[CATALOG-VALIDATION] {} param={} type mismatch", reqLine(req), ex.getName());
        return build(CatalogErrorCode.FIELD_VALIDATION_FAILED,
                Map.of("fields", Map.of(ex.getName(), "invalid_type")));
    }

    private String reqLine(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String qs = req.getQueryString();
        return req.getMethod() + " " + req.getRequestURI() + (qs != null ? "?" + qs : "");
    }

    /** R 包络：{code, message(locale), data=details}；HTTP 状态取码高 3 位 */
    private ResponseEntity<R<Object>> build(CatalogErrorCode code, Map<String, Object> details) {
        String message = messageResolver.resolve(code, RequestLocaleContext.get());
        return ResponseEntity.status(code.getHttpStatus()).body(new R<>(code.getCode(), message, details));
    }
}
