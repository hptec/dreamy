package com.dreamy.error;

import com.dreamy.i18n.MessageResolver;
import com.dreamy.i18n.RequestLocaleContext;
import huihao.web.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public ResponseEntity<R<Object>> handleInfra(InfraException ex, HttpServletRequest req) {
        log.error("[INFRA] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails(), ex);
        return build(ex.getErrorCode(), ex.getDetails());
    }

    /** 业务异常（EX-01~25）：4xx WARN（LOG-02），透传映射 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Object>> handleBiz(BizException ex, HttpServletRequest req) {
        log.warn("[BIZ] {} code={} details={}", reqLine(req), ex.getErrorCode().getCode(), ex.getDetails());
        return build(ex.getErrorCode(), ex.getDetails());
    }

    /** Bean Validation 字段校验失败（EX-02）→ 40000 VALIDATION_ERROR + 字段级 details */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("[VALIDATION] {} details={}", reqLine(req), details);
        return build(ErrorCode.VALIDATION_ERROR, details);
    }

    /**
     * 无匹配路由/静态资源（访问不存在的路径）→ 40400 NOT_FOUND。
     * 这是正常现象（探针打 /、爬虫扫描），不属于服务器内部错误：
     * 不刷 ERROR 堆栈、不返回 500；DEBUG 级留痕即可（符合「没问题不打日志」）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Object>> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        log.debug("[NOT_FOUND] {}", reqLine(req));
        return build(ErrorCode.NOT_FOUND, null);
    }

    /** 兜底：未预期异常（PATH-04 EX-30）→ 50000 INTERNAL_ERROR，不暴露细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Object>> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("[INTERNAL] {} unexpected error", reqLine(req), ex);
        return build(ErrorCode.INTERNAL_ERROR, null);
    }

    /** 请求行：method + path + query，定位是哪个接口、带了什么参数（仅出错时记录） */
    private String reqLine(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String qs = req.getQueryString();
        return req.getMethod() + " " + req.getRequestURI() + (qs != null ? "?" + qs : "");
    }

    /**
     * 统一错误响应：huihao-base R 包络 {code,message,data}。
     * 因 R 无 details 字段，字段级错误细节装入 data；保留精细 HTTP 状态码（决策 7）。
     */
    private ResponseEntity<R<Object>> build(ErrorCode code, Map<String, Object> details) {
        String message = messageResolver.resolve(code, RequestLocaleContext.get());
        R<Object> body = new R<>(code.getCode(), message, details);
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }
}
