package com.dreamy.marketing.infra;

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
 * marketing 域操作审计记录器（BE-DIM-7）。
 * 复用 identity AuditService（operation_log 只读不可删）；审计行在事务内写入
 * （api-detail STEP「INSERT operation_log」属 TX-MKT-* 边界）并携带 changes before/after JSON。
 * 本域 25 个 action 枚举见 marketing-api-detail §0（Banner 行内 Toggle 归入「编辑Banner」）。
 */
@Component
public class MarketingAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(MarketingAuditRecorder.class);

    private final AuditService auditService;
    private final AdminUserMapper adminUserMapper;

    public MarketingAuditRecorder(AuditService auditService, AdminUserMapper adminUserMapper) {
        this.auditService = auditService;
        this.adminUserMapper = adminUserMapper;
    }

    /** 写 operation_log（事务内调用，随 TX 原子提交/回滚） */
    public void record(String action, String target, String changesJson) {
        try {
            Long operatorId = null;
            AuthPrincipal principal = AuthContext.get();
            if (principal != null && principal.subject() != null) {
                try {
                    operatorId = Long.parseLong(principal.subject());
                } catch (NumberFormatException ignored) {
                    // 非数字主体按系统操作记录
                }
            }
            auditService.record(operatorId, resolveOperatorName(operatorId), action, target,
                    extractIp(), extractUa(), changesJson);
        } catch (Exception ex) {
            // 审计写入失败不应吞掉事务语义之外的异常——记录并继续（与 identity AuditAspect 同口径）
            log.warn("[AUDIT-MKT] record failed action={} target={}", action, target, ex);
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
        } catch (Exception ignored) {
            // 回退 id 字符串
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
