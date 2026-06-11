package com.dreamy.shipping.infra;

import com.dreamy.identity.domain.admin.entity.AdminUser;
import com.dreamy.identity.domain.admin.repository.AdminUserMapper;
import com.dreamy.identity.domain.audit.service.AuditService;
import com.dreamy.identity.security.AuthContext;
import com.dreamy.identity.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * shipping 域操作审计记录器（BE-DIM-7）。
 * 复用 identity AuditService（operation_log 只读不可删）；审计行在事务内写入
 * （api-detail STEP「INSERT operation_log」属 TX-SHP-* 边界，业务失败审计不留痕）
 * 且携带 changes before/after JSON。本域 7 个 action 枚举见 shipping-api-detail §11。
 */
@Component
public class ShippingAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(ShippingAuditRecorder.class);

    private final AuditService auditService;
    private final AdminUserMapper adminUserMapper;

    public ShippingAuditRecorder(AuditService auditService, AdminUserMapper adminUserMapper) {
        this.auditService = auditService;
        this.adminUserMapper = adminUserMapper;
    }

    /** 写 operation_log（事务内调用，随 TX 原子提交/回滚） */
    public void record(String action, String target, String changesJson) {
        Long operatorId = null;
        AuthPrincipal principal = AuthContext.get();
        if (principal != null && principal.subject() != null) {
            try {
                operatorId = Long.parseLong(principal.subject());
            } catch (NumberFormatException ignored) {
                // 非数字主体（不应出现）按系统操作记录
            }
        }
        try {
            auditService.record(operatorId, resolveOperatorName(operatorId), action, target,
                    extractIp(), extractUa(), changesJson);
        } catch (Exception ex) {
            // 审计写入失败记录并继续，不回滚业务事务（与 identity AuditAspect / 其余域 AuditRecorder 同口径）
            log.warn("[AUDIT-SHP] record failed action={} target={}", action, target, ex);
        }
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) {
            return "系统";
        }
        try {
            AdminUser admin = adminUserMapper.selectById(operatorId);
            if (admin != null && admin.getName() != null) {
                return admin.getName();
            }
        } catch (Exception ex) {
            log.debug("[AUDIT-SHP] resolve operator name failed id={}", operatorId);
        }
        return String.valueOf(operatorId);
    }

    private String extractIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            String forwarded = attrs.getRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
            String ip = attrs.getRequest().getRemoteAddr();
            if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
                return "127.0.0.1";
            }
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
