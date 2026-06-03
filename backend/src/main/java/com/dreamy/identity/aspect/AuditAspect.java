package com.dreamy.identity.aspect;

import com.dreamy.identity.domain.admin.entity.AdminUserEntity;
import com.dreamy.identity.domain.admin.repository.AdminUserMapper;
import com.dreamy.identity.domain.audit.service.AuditService;
import com.dreamy.identity.security.AuthContext;
import com.dreamy.identity.security.AuthPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作审计 AOP 切面（FLOW-17）。
 * 约束: FUNC-024；RM-100（仅 insert）；EDGE-018（只读无 delete）；MAP-006 operator_name 快照。
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;
    private final AdminUserMapper adminUserMapper;

    public AuditAspect(AuditService auditService, AdminUserMapper adminUserMapper) {
        this.auditService = auditService;
        this.adminUserMapper = adminUserMapper;
    }

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Object result = pjp.proceed();
        try {
            AuthPrincipal p = AuthContext.get();
            Long operatorId = p != null && p.subject() != null ? Long.parseLong(p.subject()) : null;
            String operatorName = resolveOperatorName(operatorId);
            String target = auditLog.target().isEmpty() ? extractTarget(result, pjp.getArgs()) : auditLog.target();
            String ip = extractIp();
            String ua = extractUa();
            auditService.record(operatorId, operatorName, auditLog.action(), target, ip, ua, null);
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
        return result;
    }

    /** 从返回值自动提取操作对象名，失败时从参数回退 */
    @SuppressWarnings("unchecked")
    private String extractTarget(Object result, Object[] args) {
        String name = extractFromResult(result);
        if (name != null) return name;
        return extractFromArgs(args);
    }

    /** 从 ResponseEntity<R<DTO>> 提取 DTO.name() */
    private String extractFromResult(Object result) {
        try {
            if (result == null) return null;
            // ResponseEntity → getBody() 取 R<T>
            Object body = result.getClass().getMethod("getBody").invoke(result);
            if (body == null) return null;
            // R<T> → getData() 取 DTO
            Object data = body.getClass().getMethod("getData").invoke(body);
            if (data == null) return null;
            // AdminDTO / RoleDTO / UserProfileDTO 都有 name()
            try { return (String) data.getClass().getMethod("name").invoke(data); } catch (Exception ignored) {}
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 从方法参数提取操作对象标识（回退：用于 R<Void> 类操作如 delete/reset/forceLogout） */
    private String extractFromArgs(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Long id) return "ID:" + id;
        }
        return null;
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) return "系统";
        try {
            AdminUserEntity admin = adminUserMapper.selectById(operatorId);
            if (admin != null && admin.getName() != null) return admin.getName();
        } catch (Exception ignored) {
        }
        return String.valueOf(operatorId);
    }

    private String extractIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            // 优先取代理头（X-Forwarded-For 首个地址）
            String forwarded = attrs.getRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) return first;
            }
            String ip = attrs.getRequest().getRemoteAddr();
            // 本地 IPv6 回环转 IPv4
            if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
            return ip;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractUa() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getHeader("User-Agent") : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
