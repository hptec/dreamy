package com.dreamy.error;

import com.dreamy.controller.AdminBannerController;
import com.dreamy.controller.AdminBlogController;
import com.dreamy.controller.AdminCouponController;
import com.dreamy.controller.AdminFlashSaleController;
import com.dreamy.controller.AdminGuideController;
import com.dreamy.controller.AdminLookbookController;
import com.dreamy.controller.AdminWeddingController;
import com.dreamy.controller.AdminHomePageSectionController;
import com.dreamy.controller.AdminNavigationController;
import com.dreamy.controller.AdminAnnouncementController;
import com.dreamy.controller.StoreContentController;
import com.dreamy.controller.StoreLeadController;
import com.dreamy.controller.StorePromotionController;
import com.dreamy.controller.StoreSiteBuilderController;

import com.dreamy.i18n.RequestLocaleContext;
import com.dreamy.i18n.MarketingMessageResolver;
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
 * marketing 域异常处理器（仅作用于 marketing 域 Controller（assignableTypes 枚举，包合并后替代 basePackages 隔离），优先于 identity GlobalExceptionHandler）。
 * 约束: error-strategy R 包络（失败 {code,message,data=details}）；422704 字段级结构
 * `{ fields: { <field>: <reason_key> } }`（marketing-api-detail §0 横切）；4xx WARN / 5xx ERROR 分级。
 * identity 复用码（40100/40300/50000 等 BizException）仍由 identity GlobalExceptionHandler 兜底处理。
 */
@RestControllerAdvice(assignableTypes = {AdminBannerController.class, AdminBlogController.class, AdminCouponController.class, AdminFlashSaleController.class, AdminGuideController.class, AdminLookbookController.class, AdminWeddingController.class, AdminHomePageSectionController.class, AdminNavigationController.class, AdminAnnouncementController.class, StoreContentController.class, StoreLeadController.class, StorePromotionController.class, StoreSiteBuilderController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MarketingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketingExceptionHandler.class);

    private final MarketingMessageResolver messageResolver;

    public MarketingExceptionHandler(MarketingMessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    /** marketing 域业务异常 → 6 位码映射 */
    @ExceptionHandler(MarketingException.class)
    public ResponseEntity<R<Object>> handleMarketing(MarketingException ex, HttpServletRequest req) {
        if (ex.getErrorCode().getHttpStatus() >= 500) {
            log.error("[MKT] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails(), ex);
        } else {
            log.warn("[MKT] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        }
        return build(ex.getErrorCode(), ex.getDetails());
    }

    /** site_builder 域业务异常 → 6 位码映射（KD-15 assignableTypes 复用 MarketingExceptionHandler） */
    @ExceptionHandler(SiteBuilderException.class)
    public ResponseEntity<R<Object>> handleSiteBuilder(SiteBuilderException ex, HttpServletRequest req) {
        if (ex.getErrorCode().getHttpStatus() >= 500) {
            log.error("[SB] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails(), ex);
        } else {
            log.warn("[SB] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        }
        return buildSiteBuilder(ex.getErrorCode(), ex.getDetails());
    }

    /** Bean Validation 字段校验失败 → 422704 + fields 字典（error-strategy L2 要求 1） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        }
        log.warn("[MKT-VALIDATION] {} fields={}", reqLine(req), fields);
        return build(MarketingErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", fields));
    }

    /** 请求体不可读（JSON 解析失败/枚举反序列化失败/类型错位）→ 422704（CV-MKT-001 枚举落点） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("[MKT-VALIDATION] {} unreadable body", reqLine(req));
        return build(MarketingErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", Map.of("_body", "malformed")));
    }

    /** 查询参数类型不匹配 → 422704 fields.{param}=invalid_type */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        log.warn("[MKT-VALIDATION] {} param={} type mismatch", reqLine(req), ex.getName());
        return build(MarketingErrorCode.FIELD_VALIDATION_FAILED,
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
    private ResponseEntity<R<Object>> build(MarketingErrorCode code, Map<String, Object> details) {
        String message = messageResolver.resolve(code, RequestLocaleContext.get());
        return ResponseEntity.status(code.getHttpStatus()).body(new R<>(code.getCode(), message, details));
    }

    /** site_builder 域 R 包络（message 暂用 code，后续接入 site_builder message bundle） */
    private ResponseEntity<R<Object>> buildSiteBuilder(SiteBuilderErrorCode code, Map<String, Object> details) {
        String message = code.name();
        return ResponseEntity.status(code.getHttpStatus()).body(new R<>(code.getCode(), message, details));
    }
}
