package com.dreamy.infra;

import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.audit.service.AuditService;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * review 域操作审计记录器（BE-DIM-7）。复用 identity AuditService（operation_log 只读不可删），
 * 审计行在事务内写入（review-api-detail STEP「INSERT operation_log」属 TX-REV-* 边界）。
 * 本域 action 枚举严格沿用 error-strategy 权威清单 3 个：评价审核 / 评价批量操作 / 回答提问。
 * 归入规则（§0 横切）：设/取消精选、官方回复创建/编辑/删除、图片驳回/恢复 → 归入「评价审核」
 * （changes 记录子类型 + before/after）；Q&A 可见性切换 → 归入「回答提问」（changes 记录 visible from/to）。
 */
@Component
public class ReviewAuditRecorder {

    /** error-strategy 权威 action 枚举（本域 3 个） */
    public static final String ACTION_MODERATE = "评价审核";
    public static final String ACTION_BATCH = "评价批量操作";
    public static final String ACTION_ANSWER = "回答提问";

    private static final Logger log = LoggerFactory.getLogger(ReviewAuditRecorder.class);

    private final AuditService auditService;
    private final AdminUserMapper adminUserMapper;

    public ReviewAuditRecorder(AuditService auditService, AdminUserMapper adminUserMapper) {
        this.auditService = auditService;
        this.adminUserMapper = adminUserMapper;
    }

    /** 写 operation_log（事务内调用，随 TX 原子提交/回滚）；store 提交端点不调用（非后台操作） */
    public void record(String action, String target, String changesJson) {
        try {
            Long operatorId = null;
            AuthPrincipal principal = AuthContext.get();
            if (principal != null && principal.subject() != null) {
                try {
                    operatorId = Long.parseLong(principal.subject());
                } catch (NumberFormatException ignored) {
                    // 非数字主体（不应出现）按系统操作记录
                }
            }
            auditService.record(operatorId, resolveOperatorName(operatorId), action, target,
                    extractIp(), extractUa(), changesJson);
        } catch (Exception ex) {
            // 审计写入失败记录并继续（与 identity AuditAspect 同口径）
            log.warn("[AUDIT-REV] record failed action={} target={}", action, target, ex);
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
