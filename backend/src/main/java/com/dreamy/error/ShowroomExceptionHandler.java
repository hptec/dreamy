package com.dreamy.error;

import com.dreamy.controller.StoreShowroomController;

import com.dreamy.error.CatalogException;
import com.dreamy.i18n.CatalogMessageResolver;
import com.dreamy.i18n.RequestLocaleContext;
import com.dreamy.i18n.ShowroomMessageResolver;
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
 * showroom 域异常处理器（仅作用于 showroom 域 Controller（assignableTypes 枚举，包合并后替代 basePackages 隔离），优先于 identity GlobalExceptionHandler）。
 * 约束: error-strategy R 包络（失败 {code,message,data=details}）；422101 字段级结构
 * `{ fields: { <field>: <reason_key> } }`（showroom-api-detail §0 横切）；4xx WARN / 5xx ERROR 分级。
 * 商品引用校验透传 catalog 码（404501，review/trading 同先例）；
 * path 参数类型非法视同不存在（V-SHR-003/015/020 口径：id→404101 / itemId→404102 / memberId→404103，防探测）。
 */
@RestControllerAdvice(assignableTypes = {StoreShowroomController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShowroomExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ShowroomExceptionHandler.class);

    private final ShowroomMessageResolver messageResolver;
    private final CatalogMessageResolver catalogMessageResolver;

    public ShowroomExceptionHandler(ShowroomMessageResolver messageResolver,
                                    CatalogMessageResolver catalogMessageResolver) {
        this.messageResolver = messageResolver;
        this.catalogMessageResolver = catalogMessageResolver;
    }

    /** showroom 域业务异常 → 6 位码映射 */
    @ExceptionHandler(ShowroomException.class)
    public ResponseEntity<R<Object>> handleShowroom(ShowroomException ex, HttpServletRequest req) {
        if (ex.getErrorCode().getHttpStatus() >= 500) {
            log.error("[SHOWROOM] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(),
                    ex.getDetails(), ex);
        } else {
            log.warn("[SHOWROOM] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(),
                    ex.getDetails());
        }
        String message = messageResolver.resolve(ex.getErrorCode(), RequestLocaleContext.get());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(new R<>(ex.getErrorCode().getCode(), message, ex.getDetails()));
    }

    /** catalog 域异常透传（V-SHR-013 商品存在性校验 → 404501，review/trading 先例） */
    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<R<Object>> handleCatalogPassthrough(CatalogException ex, HttpServletRequest req) {
        log.warn("[SHOWROOM] {} passthrough catalog code={}", reqLine(req), ex.getErrorCode().getCode());
        String message = catalogMessageResolver.resolve(ex.getErrorCode(), RequestLocaleContext.get());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(new R<>(ex.getErrorCode().getCode(), message, ex.getDetails()));
    }

    /** Bean Validation 字段校验失败 → 422101 + fields 字典（error-strategy L2 要求 1） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        }
        log.warn("[SHOWROOM-VALIDATION] {} fields={}", reqLine(req), fields);
        return build422(Map.of("fields", fields));
    }

    /** 请求体不可读（JSON 解析失败/类型错位）→ 422101 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("[SHOWROOM-VALIDATION] {} unreadable body", reqLine(req));
        return build422(Map.of("fields", Map.of("_body", "malformed")));
    }

    /**
     * path 参数类型不匹配 → 非法视同不存在（V-SHR-003/015/020：id→404101 / itemId→404102 /
     * memberId→404103，防探测）；其余参数 → 422101 fields.{param}=invalid_type。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                        HttpServletRequest req) {
        String name = ex.getName();
        log.warn("[SHOWROOM-VALIDATION] {} param={} type mismatch", reqLine(req), name);
        ShowroomErrorCode notFound = switch (name) {
            case "id" -> ShowroomErrorCode.SHOWROOM_NOT_FOUND;
            case "itemId" -> ShowroomErrorCode.SHOWROOM_ITEM_NOT_FOUND;
            case "memberId" -> ShowroomErrorCode.SHOWROOM_MEMBER_NOT_FOUND;
            default -> null;
        };
        if (notFound != null) {
            String message = messageResolver.resolve(notFound, RequestLocaleContext.get());
            return ResponseEntity.status(notFound.getHttpStatus())
                    .body(new R<>(notFound.getCode(), message, null));
        }
        return build422(Map.of("fields", Map.of(name, "invalid_type")));
    }

    private ResponseEntity<R<Object>> build422(Map<String, Object> details) {
        String message = messageResolver.resolve(ShowroomErrorCode.FIELD_VALIDATION_FAILED,
                RequestLocaleContext.get());
        return ResponseEntity.status(422)
                .body(new R<>(ShowroomErrorCode.FIELD_VALIDATION_FAILED.getCode(), message, details));
    }

    private String reqLine(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String qs = req.getQueryString();
        return req.getMethod() + " " + req.getRequestURI() + (qs != null ? "?" + qs : "");
    }
}
