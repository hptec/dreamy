package com.dreamy.trading.error;

import com.dreamy.catalog.error.CatalogException;
import com.dreamy.catalog.i18n.CatalogMessageResolver;
import com.dreamy.identity.i18n.RequestLocaleContext;
import com.dreamy.infra.stripe.StripeException;
import com.dreamy.infra.stripe.StripeTimeoutException;
import com.dreamy.marketing.error.MarketingException;
import com.dreamy.marketing.i18n.MarketingMessageResolver;
import com.dreamy.trading.i18n.TradingMessageResolver;
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
 * trading 域异常处理器（仅作用于 com.dreamy.trading 包内 Controller，优先于 identity GlobalExceptionHandler）。
 * 约束: error-strategy R 包络（失败 {code,message,data=details}）；422 字段级 `{fields:{field:reason_key}}`；
 * 跨域透传：404501 CatalogException（V-TRD-002/037/042）/ 422701~422703 MarketingException
 * （createOrder.STEP-TRD-05 ④ 核销失败透传）按各域码与文案口径输出；
 * Stripe 基础设施异常 → 502601/504601（BE-DIM-5 降级矩阵）。
 * identity 复用码（40100/40300/50000 等 BizException）仍由 identity GlobalExceptionHandler 兜底处理。
 */
@RestControllerAdvice(basePackages = "com.dreamy.trading")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TradingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TradingExceptionHandler.class);

    private final TradingMessageResolver messageResolver;
    private final CatalogMessageResolver catalogMessageResolver;
    private final MarketingMessageResolver marketingMessageResolver;

    public TradingExceptionHandler(TradingMessageResolver messageResolver,
                                   CatalogMessageResolver catalogMessageResolver,
                                   MarketingMessageResolver marketingMessageResolver) {
        this.messageResolver = messageResolver;
        this.catalogMessageResolver = catalogMessageResolver;
        this.marketingMessageResolver = marketingMessageResolver;
    }

    /** trading 域业务异常 → 6 位码映射 */
    @ExceptionHandler(TradingException.class)
    public ResponseEntity<R<Object>> handleTrading(TradingException ex, HttpServletRequest req) {
        if (ex.getErrorCode().getHttpStatus() >= 500) {
            log.error("[TRADING] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails(), ex);
        } else {
            log.warn("[TRADING] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        }
        return build(ex.getErrorCode(), ex.getDetails());
    }

    /** catalog 域透传（404501 商品不存在或未发布，trading-api-detail V-TRD-002 口径） */
    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<R<Object>> handleCatalogPassthrough(CatalogException ex, HttpServletRequest req) {
        log.warn("[TRADING-CATALOG] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        String message = catalogMessageResolver.resolve(ex.getErrorCode(), RequestLocaleContext.get());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(new R<>(ex.getErrorCode().getCode(), message, ex.getDetails()));
    }

    /** marketing 域透传（422701/422702/422703 券核销失败，createOrder.STEP-TRD-05 ④） */
    @ExceptionHandler(MarketingException.class)
    public ResponseEntity<R<Object>> handleMarketingPassthrough(MarketingException ex, HttpServletRequest req) {
        log.warn("[TRADING-MARKETING] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        String message = marketingMessageResolver.resolve(ex.getErrorCode(), RequestLocaleContext.get());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(new R<>(ex.getErrorCode().getCode(), message, ex.getDetails()));
    }

    /** Stripe 基础设施异常 → 502601/504601（订单保持 pending / 退款事务整体回滚后到达此处） */
    @ExceptionHandler(StripeException.class)
    public ResponseEntity<R<Object>> handleStripe(StripeException ex, HttpServletRequest req) {
        TradingErrorCode code = ex instanceof StripeTimeoutException
                ? TradingErrorCode.STRIPE_TIMEOUT : TradingErrorCode.STRIPE_UNAVAILABLE;
        // 5xx：不泄露 Stripe 负载细节（error-strategy 脱敏规则）
        log.error("[TRADING-STRIPE] {} code={}", reqLine(req), code.getCode(), ex);
        return build(code, null);
    }

    /** Bean Validation 字段校验失败 → 422601 + fields 字典 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        }
        log.warn("[TRADING-VALIDATION] {} fields={}", reqLine(req), fields);
        return build(TradingErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", fields));
    }

    /** 请求体不可读（JSON 解析失败/类型错位）→ 422601 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("[TRADING-VALIDATION] {} unreadable body", reqLine(req));
        return build(TradingErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", Map.of("_body", "malformed")));
    }

    /** 查询/路径参数类型不匹配 → 422601 fields.{param}=invalid_type */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        log.warn("[TRADING-VALIDATION] {} param={} type mismatch", reqLine(req), ex.getName());
        return build(TradingErrorCode.FIELD_VALIDATION_FAILED,
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
    private ResponseEntity<R<Object>> build(TradingErrorCode code, Map<String, Object> details) {
        String message = messageResolver.resolve(code, RequestLocaleContext.get());
        return ResponseEntity.status(code.getHttpStatus()).body(new R<>(code.getCode(), message, details));
    }
}
