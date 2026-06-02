package com.dreamy.identity.admin.aspect;

import com.dreamy.identity.common.domain.service.AuditService;
import com.dreamy.identity.common.security.AuthContext;
import com.dreamy.identity.common.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作审计 AOP 切面（FLOW-17）。
 * 约束: FUNC-024；RM-100（仅 insert）；EDGE-018（只读无 delete）；MAP-006 operator_name 快照。
 * 标注 @AuditLog 的 admin 写操作自动写 operation_log。
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Object result = pjp.proceed();
        try {
            AuthPrincipal p = AuthContext.get();
            String operatorId = p != null ? p.subject() : null;
            String operatorName = operatorId != null ? operatorId : "系统";
            String ip = extractIp();
            String ua = extractUa();
            auditService.record(operatorId, operatorName, auditLog.action(),
                    auditLog.target(), ip, ua, null);
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
        return result;
    }

    private String extractIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractUa() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
