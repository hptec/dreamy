package com.dreamy.trading.infra;

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
 * trading 域操作审计记录器（BE-DIM-7；复用 identity AuditService operation_log，只读不可删）。
 * 本域 7 个 action 枚举（trading-api-detail §0）：订单发货 / 订单状态变更 / 发起退款 /
 * 退款审核通过 / 退款审核拒绝 / 汇率变更 / 结算配置变更。
 * 审计行在事务内写入（属 TX-TRD-003/004/009/011/012 边界），随 TX 原子提交/回滚。
 */
@Component
public class TradingAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(TradingAuditRecorder.class);

    public static final String ACTION_ORDER_SHIP = "订单发货";
    public static final String ACTION_ORDER_STATUS = "订单状态变更";
    public static final String ACTION_REFUND_CREATE = "发起退款";
    public static final String ACTION_REFUND_APPROVE = "退款审核通过";
    public static final String ACTION_REFUND_REJECT = "退款审核拒绝";
    public static final String ACTION_RATE_UPDATE = "汇率变更";
    public static final String ACTION_CHECKOUT_CONFIG = "结算配置变更";

    private final AuditService auditService;
    private final AdminUserMapper adminUserMapper;

    public TradingAuditRecorder(AuditService auditService, AdminUserMapper adminUserMapper) {
        this.auditService = auditService;
        this.adminUserMapper = adminUserMapper;
    }

    /** 写 operation_log（事务内调用）。日志脱敏：target 仅记 order_no/refund_no，不含 PII。 */
    public void record(String action, String target, String changesJson) {
        try {
            Long operatorId = currentOperatorId();
            auditService.record(operatorId, resolveOperatorName(operatorId), action, target,
                    extractIp(), extractUa(), changesJson);
        } catch (Exception ex) {
            // 审计写入失败不应吞掉事务语义之外的异常——记录并继续（与 identity AuditAspect 同口径）
            log.warn("[AUDIT-TRD] record failed action={} target={}", action, target, ex);
        }
    }

    /** 当前后台操作者 id（汇率 updated_by 等列复用） */
    public Long currentOperatorId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.subject() == null) {
            return null;
        }
        try {
            return Long.parseLong(principal.subject());
        } catch (NumberFormatException ignored) {
            return null;
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
